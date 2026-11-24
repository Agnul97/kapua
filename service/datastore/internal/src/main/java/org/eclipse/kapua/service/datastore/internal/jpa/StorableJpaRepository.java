/*******************************************************************************
 * Copyright (c) 2026 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.datastore.internal.jpa;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.cache.LocalCache;
import org.eclipse.kapua.commons.jpa.JpaAwareTxContext;
import org.eclipse.kapua.commons.jpa.JpaQueryPredicateTranslator;
import org.eclipse.kapua.commons.jpa.KapuaJpaRepositoryConfiguration;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.model.query.predicate.AndPredicateImpl;
import org.eclipse.kapua.commons.model.query.predicate.AttributePredicateImpl;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.query.predicate.QueryPredicate;
import org.eclipse.kapua.service.storable.StorableFactory;
import org.eclipse.kapua.service.storable.model.Storable;
import org.eclipse.kapua.service.storable.model.StorableListResult;
import org.eclipse.kapua.service.storable.model.id.StorableId;
import org.eclipse.kapua.service.storable.model.query.SortDirection;
import org.eclipse.kapua.service.storable.model.query.SortField;
import org.eclipse.kapua.service.storable.model.query.StorableQuery;
import org.eclipse.kapua.service.storable.repository.StorableRepository;
import org.eclipse.kapua.storage.TxManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * JPA implementation of {@link StorableRepository}, the SQL counterpart of {@code DeviceStoreRepository} (Elasticsearch).
 * <p>
 * Built from Kapua's JPA pieces: {@link TxManager} transactions on the {@code kapua-datastore} persistence unit, the {@link JpaQueryPredicateTranslator}
 * extracted from {@code KapuaEntityJpaRepository}, the same scope handling ({@link KapuaId#ANY} = all scopes) and the same limit + 1 detection of
 * {@code limitExceeded}. The storable predicates are adapted to Kapua predicates by the {@link StorablePredicateAdapter}.
 * <p>
 * {@link StorableRepository} methods take no transaction context, so each call runs in its own transaction.
 *
 * @param <T> the {@link Storable} type
 * @param <C> the JPA entity class implementing {@code T}
 * @param <L> the list result type
 * @param <Q> the query type
 * @since 2.1.0
 */
public class StorableJpaRepository<T extends Storable, C extends T, L extends StorableListResult<T>, Q extends StorableQuery> implements StorableRepository<T, L, Q> {

    private static final Logger LOG = LoggerFactory.getLogger(StorableJpaRepository.class);

    /**
     * SQLState class 23 = integrity constraint violation (duplicate key on H2 is 23505, on MariaDB 23000).
     */
    private static final String SQL_STATE_CLASS_INTEGRITY_CONSTRAINT = "23";

    private final Class<C> concreteClass;
    private final StorableFactory<T, L, Q> storableFactory;
    private final TxManager txManager;
    private final InfoFieldMapping fieldMapping;
    private final StorablePredicateAdapter predicateAdapter;
    private final JpaQueryPredicateTranslator predicateTranslator;
    /**
     * The facades' item cache ("this item exists, do not upsert it again"), the same {@code LocalCache} the Elasticsearch repository receives as
     * {@code indexUpserted}. There it also caches index names; here it is only invalidated, in the same methods as there, so that after data is wiped
     * the facades recreate the items.
     */
    private final LocalCache<String, Boolean> itemsCache;
    private final Function<T, StorableId> idExtractor;

    public StorableJpaRepository(Class<C> concreteClass,
                                 StorableFactory<T, L, Q> storableFactory,
                                 TxManager txManager,
                                 KapuaJpaRepositoryConfiguration jpaRepositoryConfiguration,
                                 InfoFieldMapping fieldMapping,
                                 LocalCache<String, Boolean> itemsCache,
                                 Function<T, StorableId> idExtractor) {
        this.concreteClass = concreteClass;
        this.storableFactory = storableFactory;
        this.txManager = txManager;
        this.fieldMapping = fieldMapping;
        this.predicateAdapter = new StorablePredicateAdapter(fieldMapping);
        this.predicateTranslator = new JpaQueryPredicateTranslator(jpaRepositoryConfiguration);
        this.itemsCache = itemsCache;
        this.idExtractor = idExtractor;
    }

    // Writes

    /**
     * Inserts or updates the item ({@code EntityManager.merge}). When two consumers insert the same new item at the same time, the second commit fails with a
     * duplicate key: it is retried once, and the retried merge finds the row and becomes an update.
     */
    @Override
    public String upsert(String itemId, T item) {
        executeWithDuplicateKeyRetry(tx -> {
            em(tx).merge(item);
            return null;
        });
        return itemId;
    }

    @Override
    public Set<String> upsert(List<T> items) {
        executeWithDuplicateKeyRetry(tx -> {
            EntityManager em = em(tx);
            for (T item : items) {
                em.merge(item);
            }
            return null;
        });
        Set<String> ids = new LinkedHashSet<>();
        for (T item : items) {
            StorableId id = idExtractor.apply(item);
            ids.add(id != null ? id.toString() : null);
        }
        return ids;
    }

    @Override
    public void delete(KapuaId scopeId, StorableId id) {
        execute(tx -> {
            EntityManager em = em(tx);
            C found = em.find(concreteClass, id.toString());
            if (found != null && isInScope(found, scopeId)) {
                em.remove(found);
            }
            return null;
        });
    }

    @Override
    public void delete(Q query) {
        // adapted before the transaction: an unsupported predicate surfaces as such, not wrapped by the transaction manager
        QueryPredicate predicate = scoped(query);
        execute(tx -> {
            EntityManager em = em(tx);
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaDelete<C> delete = cb.createCriteriaDelete(concreteClass);
            Root<C> root = delete.from(concreteClass);
            Map<ParameterExpression, Object> binds = new HashMap<>();
            Predicate where = toWhere(predicate, binds, cb, root);
            if (where != null) {
                delete.where(where);
            }
            Query jpaQuery = em.createQuery(delete);
            binds.forEach(jpaQuery::setParameter);
            jpaQuery.executeUpdate();
            return null;
        });
    }

    // Reads

    @Override
    public T find(KapuaId scopeId, StorableId id) {
        return execute(tx -> {
            C found = em(tx).find(concreteClass, id.toString());
            return found != null && isInScope(found, scopeId) ? found : null;
        });
    }

    @Override
    public L query(Q query) {
        QueryPredicate predicate = scoped(query);
        List<SortField> sortFields = effectiveSort(query);
        return execute(tx -> {
            EntityManager em = em(tx);
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<C> select = cb.createQuery(concreteClass);
            Root<C> root = select.from(concreteClass);
            select.select(root);

            Map<ParameterExpression, Object> binds = new HashMap<>();
            List<Predicate> where = new ArrayList<>();
            Predicate filter = toWhere(predicate, binds, cb, root);
            if (filter != null) {
                where.add(filter);
            }

            if (!where.isEmpty()) {
                select.where(where.toArray(new Predicate[0]));
            }
            select.orderBy(toOrders(cb, root, sortFields));

            TypedQuery<C> jpaQuery = em.createQuery(select);
            binds.forEach(jpaQuery::setParameter);
            if (query.getOffset() != null) {
                jpaQuery.setFirstResult(query.getOffset());
            }
            // limit + 1 to know whether more results exist, as KapuaEntityJpaRepository does
            if (query.getLimit() != null) {
                jpaQuery.setMaxResults(query.getLimit() + 1);
            }
            List<C> rows = new ArrayList<>(jpaQuery.getResultList());

            L result = storableFactory.newListResult();
            if (query.getLimit() != null && rows.size() > query.getLimit()) {
                rows.remove(query.getLimit().intValue());
                result.setLimitExceeded(true);
            }
            result.addItems(rows);
            // The Elasticsearch repository always returns the total hits, so callers may rely on it: always counted here too.
            result.setTotalCount(doCount(em, predicate));
            return result;
        });
    }

    @Override
    public long count(Q query) {
        QueryPredicate predicate = scoped(query);
        return execute(tx -> doCount(em(tx), predicate));
    }

    // Index-shaped methods of StorableRepository: only tests call them

    /**
     * Nothing to refresh in SQL (a committed row is visible at once). Invalidates the items cache as the Elasticsearch repository does.
     */
    @Override
    public void refreshAllIndexes() {
        itemsCache.invalidateAll();
    }

    /**
     * Nothing to refresh in SQL. Invalidates the items cache as the Elasticsearch repository does.
     */
    @Override
    public void refreshIndex(String indexExp) {
        itemsCache.invalidateAll();
    }

    /**
     * Deletes every row, so test scenarios start clean, and invalidates the items cache so the facades recreate items afterwards.
     */
    @Override
    public void deleteAllIndexes() {
        execute(tx -> {
            EntityManager em = em(tx);
            em.createQuery(em.getCriteriaBuilder().createCriteriaDelete(concreteClass)).executeUpdate();
            return null;
        });
        itemsCache.invalidateAll();
    }

    /**
     * No-op on SQL: index expressions have no meaning here (only tests call it). Invalidates the items cache as the Elasticsearch repository does.
     */
    @Override
    public void deleteIndexes(String indexExp) {
        itemsCache.invalidateAll();
    }

    // Helpers

    private long doCount(EntityManager em, QueryPredicate predicate) throws KapuaException {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> select = cb.createQuery(Long.class);
        Root<C> root = select.from(concreteClass);
        select.select(cb.count(root));
        Map<ParameterExpression, Object> binds = new HashMap<>();
        Predicate where = toWhere(predicate, binds, cb, root);
        if (where != null) {
            select.where(where);
        }
        TypedQuery<Long> jpaQuery = em.createQuery(select);
        binds.forEach(jpaQuery::setParameter);
        return jpaQuery.getSingleResult();
    }

    /**
     * The query predicate, adapted to a Kapua predicate, plus the scope ({@link KapuaId#ANY} or {@code null} = all scopes, as in KapuaEntityJpaRepository).
     */
    private QueryPredicate scoped(Q query) {
        QueryPredicate predicate = predicateAdapter.adapt(query.getPredicate());
        KapuaId scopeId = query.getScopeId();
        if (scopeId != null && !KapuaId.ANY.equals(scopeId)) {
            QueryPredicate scopePredicate = new AttributePredicateImpl<>(InfoFieldMapping.SCOPE_ID_ATTRIBUTE, KapuaEid.parseKapuaId(scopeId));
            predicate = predicate != null ? new AndPredicateImpl(scopePredicate, predicate) : scopePredicate;
        }
        return predicate;
    }

    private Predicate toWhere(QueryPredicate predicate, Map<ParameterExpression, Object> binds, CriteriaBuilder cb, Root<C> root) throws KapuaException {
        return predicate != null ? predicateTranslator.toPredicate(predicate, binds, cb, root, root.getModel()) : null;
    }

    /**
     * The query's sort, always ending with the id so the order is total and offset paging is stable.
     */
    private List<SortField> effectiveSort(Q query) {
        List<SortField> sortFields = new ArrayList<>();
        boolean hasId = false;
        if (query.getSortFields() != null) {
            for (SortField sortField : query.getSortFields()) {
                sortFields.add(sortField);
                hasId |= InfoFieldMapping.ID_ATTRIBUTE.equals(fieldMapping.attributeOf(sortField.getField()));
            }
        }
        if (!hasId) {
            sortFields.add(SortField.ascending(InfoFieldMapping.ID_ATTRIBUTE));
        }
        return sortFields;
    }

    private List<Order> toOrders(CriteriaBuilder cb, Root<C> root, List<SortField> sortFields) {
        List<Order> orders = new ArrayList<>();
        for (SortField sortField : sortFields) {
            Path<?> path = predicateTranslator.extractAttribute(root, fieldMapping.attributeOf(sortField.getField()));
            orders.add(SortDirection.DESC.equals(sortField.getSortDirection()) ? cb.desc(path) : cb.asc(path));
        }
        return orders;
    }

    private boolean isInScope(C item, KapuaId scopeId) {
        return scopeId == null || KapuaId.ANY.equals(scopeId)
                || (item.getScopeId() != null && scopeId.getId().equals(item.getScopeId().getId()));
    }

    private <R> R execute(TxManager.TxConsumer<R> work) {
        try {
            return txManager.execute(work);
        } catch (KapuaException e) {
            throw new RuntimeException(e);
        }
    }

    private <R> R executeWithDuplicateKeyRetry(TxManager.TxConsumer<R> work) {
        try {
            return txManager.execute(work);
        } catch (Exception e) {
            if (!isIntegrityConstraintViolation(e)) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
            LOG.debug("Concurrent insert of the same item, retrying the merge as an update", e);
            return execute(work);
        }
    }

    private static boolean isIntegrityConstraintViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException) {
                String sqlState = ((SQLException) cause).getSQLState();
                return sqlState != null && sqlState.startsWith(SQL_STATE_CLASS_INTEGRITY_CONSTRAINT);
            }
        }
        return false;
    }

    private static EntityManager em(org.eclipse.kapua.storage.TxContext tx) {
        return JpaAwareTxContext.extractEntityManager(tx);
    }
}
