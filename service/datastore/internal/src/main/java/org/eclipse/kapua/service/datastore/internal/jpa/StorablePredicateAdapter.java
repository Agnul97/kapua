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

import org.eclipse.kapua.commons.model.query.predicate.AndPredicateImpl;
import org.eclipse.kapua.commons.model.query.predicate.AttributePredicateImpl;
import org.eclipse.kapua.commons.model.query.predicate.OrPredicateImpl;
import org.eclipse.kapua.model.query.predicate.AttributePredicate.Operator;
import org.eclipse.kapua.model.query.predicate.QueryPredicate;
import org.eclipse.kapua.service.storable.model.id.StorableId;
import org.eclipse.kapua.service.storable.model.query.predicate.AndPredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.ExistsPredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.IdsPredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.MatchPredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.OrPredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.RangePredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.StorablePredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.TermPredicate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns the storable query predicates (the ones the Elasticsearch repositories serialize to query DSL) into Kapua {@link QueryPredicate}s, which the
 * shared {@link org.eclipse.kapua.commons.jpa.JpaQueryPredicateTranslator} turns into JPA Criteria.
 * <p>
 * Storable predicates name fields with their Elasticsearch names ({@code client_id}, {@code timestamp}, {@code metric.name}); the
 * {@link InfoFieldMapping} of the entity turns them into JPA attribute names.
 * <ul>
 *     <li>{@link TermPredicate} → EQUAL</li>
 *     <li>{@link RangePredicate} → GREATER_THAN_OR_EQUAL min and/or LESS_THAN_OR_EQUAL max (inclusive, as the Elasticsearch {@code gte} / {@code lte})</li>
 *     <li>{@link ExistsPredicate} → NOT_NULL</li>
 *     <li>{@link IdsPredicate} → id IN (...)</li>
 *     <li>{@link MatchPredicate} (also {@code ChannelMatchPredicate}) → STARTS_WITH: in Elasticsearch it is a {@code prefix} query</li>
 *     <li>{@link AndPredicate} / {@link OrPredicate} → And / Or</li>
 * </ul>
 * Any other predicate type is refused with an {@link UnsupportedOperationException}.
 *
 * @since 2.1.0
 */
public class StorablePredicateAdapter {

    private final InfoFieldMapping fieldMapping;

    public StorablePredicateAdapter(InfoFieldMapping fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    /**
     * @param storablePredicate the predicate to adapt, possibly {@code null}.
     * @return the equivalent Kapua predicate, {@code null} when {@code storablePredicate} is {@code null}.
     * @throws UnsupportedOperationException for a predicate type without a SQL translation.
     * @throws IllegalArgumentException      for a field without a JPA attribute.
     */
    public QueryPredicate adapt(StorablePredicate storablePredicate) {
        if (storablePredicate == null) {
            return null;
        }
        if (storablePredicate instanceof TermPredicate) {
            TermPredicate term = (TermPredicate) storablePredicate;
            return new AttributePredicateImpl<>(fieldMapping.attributeOf(term.getField().field()), adaptValue(term.getValue()), Operator.EQUAL);
        }
        if (storablePredicate instanceof RangePredicate) {
            RangePredicate range = (RangePredicate) storablePredicate;
            String attribute = fieldMapping.attributeOf(range.getField().field());
            AndPredicateImpl and = new AndPredicateImpl();
            if (range.getMinValue() != null) {
                and.and(new AttributePredicateImpl<>(attribute, adaptValue(range.getMinValue()), Operator.GREATER_THAN_OR_EQUAL));
            }
            if (range.getMaxValue() != null) {
                and.and(new AttributePredicateImpl<>(attribute, adaptValue(range.getMaxValue()), Operator.LESS_THAN_OR_EQUAL));
            }
            return and;
        }
        if (storablePredicate instanceof ExistsPredicate) {
            ExistsPredicate exists = (ExistsPredicate) storablePredicate;
            return new AttributePredicateImpl<>(fieldMapping.attributeOf(exists.getName()), null, Operator.NOT_NULL);
        }
        if (storablePredicate instanceof IdsPredicate) {
            List<String> ids = ((IdsPredicate) storablePredicate).getIds().stream()
                    .map(StorableId::toString)
                    .collect(Collectors.toList());
            // a Collection value is translated into an IN (an empty list matches nothing, as in Elasticsearch)
            return new AttributePredicateImpl<>(fieldMapping.idAttribute(), ids, Operator.EQUAL);
        }
        if (storablePredicate instanceof MatchPredicate) {
            MatchPredicate match = (MatchPredicate) storablePredicate;
            return new AttributePredicateImpl<>(fieldMapping.attributeOf(match.getField().field()), match.getExpression(), Operator.STARTS_WITH);
        }
        if (storablePredicate instanceof AndPredicate) {
            AndPredicateImpl and = new AndPredicateImpl();
            for (StorablePredicate p : ((AndPredicate) storablePredicate).getPredicates()) {
                and.and(adapt(p));
            }
            return and;
        }
        if (storablePredicate instanceof OrPredicate) {
            OrPredicateImpl or = new OrPredicateImpl();
            for (StorablePredicate p : ((OrPredicate) storablePredicate).getPredicates()) {
                or.or(adapt(p));
            }
            return or;
        }
        throw new UnsupportedOperationException("Storable predicate not supported on SQL: " + storablePredicate.getClass().getName());
    }

    /**
     * Storable ids are stored as strings in the entities; other values (strings, dates, numbers, {@code KapuaId}) are passed as they are.
     */
    private static Object adaptValue(Object value) {
        return value instanceof StorableId ? value.toString() : value;
    }
}
