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
package org.eclipse.kapua.commons.jpa;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.persistence.Embedded;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;

import org.apache.commons.lang.ArrayUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.kapua.KapuaErrorCodes;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.model.query.predicate.AttributePredicateImpl;
import org.eclipse.kapua.commons.model.query.predicate.OrPredicateImpl;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.query.predicate.AndPredicate;
import org.eclipse.kapua.model.query.predicate.AttributePredicate;
import org.eclipse.kapua.model.query.predicate.MatchPredicate;
import org.eclipse.kapua.model.query.predicate.OrPredicate;
import org.eclipse.kapua.model.query.predicate.QueryPredicate;

/**
 * Translates Kapua {@link QueryPredicate}s ({@link AttributePredicate}, {@link AndPredicate}, {@link OrPredicate}, {@link MatchPredicate}) into JPA
 * Criteria {@link Predicate}s.
 * <p>
 * Extracted from {@link KapuaEntityJpaRepository} (same logic, unchanged) so that JPA repositories of entities that are not {@link org.eclipse.kapua.model.KapuaEntity}
 * can reuse it. It does not depend on the entity type: any JPA {@link Root} works.
 *
 * @since 2.1.0
 */
public class JpaQueryPredicateTranslator {

    private static final String ATTRIBUTE_SEPARATOR = ".";
    private static final String ATTRIBUTE_SEPARATOR_ESCAPED = "\\.";
    private static final String COMPARE_ERROR_MESSAGE = "Trying to compare a non-comparable value";

    private final KapuaJpaRepositoryConfiguration configuration;

    public JpaQueryPredicateTranslator(KapuaJpaRepositoryConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Handles {@link QueryPredicate}s.
     * <p>
     * It manages different types of {@link QueryPredicate} like:
     * <ul>
     * <li>{@link AttributePredicate}</li>
     * <li>{@link AndPredicate}</li>
     * <li>{@link OrPredicate}</li>
     * <li>{@link MatchPredicate}</li>
     * </ul>
     * <p>
     * It can be invoked recursively (i.e. to handle {@link AttributePredicate}s of the {@link AndPredicate}.
     *
     * @param queryPredicate The {@link QueryPredicate} to handle.
     * @param binds          The {@link Map} of the query parameter values, filled by this method: bind them to the query before running it.
     * @param cb             The JPA {@link CriteriaBuilder} of the {@link javax.persistence.Query}.
     * @param entityRoot     The JPA {@link Root} of the {@link javax.persistence.Query}.
     * @param entityType     The JPA {@link EntityType} of the {@link javax.persistence.Query}.
     * @return The handled {@link Predicate}, {@code null} for an unknown {@link QueryPredicate} type.
     * @throws KapuaException If any problem occurs.
     */
    public <E> Predicate toPredicate(@NonNull QueryPredicate queryPredicate,
            @NonNull Map<ParameterExpression, Object> binds,
            @NonNull CriteriaBuilder cb,
            @NonNull Root<E> entityRoot,
            @NonNull EntityType<E> entityType)
            throws KapuaException {
        Predicate predicate = null;
        if (queryPredicate instanceof AttributePredicate) {
            AttributePredicate<?> attributePredicate = (AttributePredicate<?>) queryPredicate;
            predicate = handleAttributePredicate(attributePredicate, binds, cb, entityRoot, entityType);
        } else if (queryPredicate instanceof AndPredicate) {
            AndPredicate andPredicate = (AndPredicate) queryPredicate;
            predicate = cb.and(handlePredicates(andPredicate.getPredicates(), binds, cb, entityRoot, entityType));
        } else if (queryPredicate instanceof OrPredicate) {
            OrPredicate orPredicate = (OrPredicate) queryPredicate;
            predicate = cb.or(handlePredicates(orPredicate.getPredicates(), binds, cb, entityRoot, entityType));
        } else if (queryPredicate instanceof MatchPredicate) {
            MatchPredicate<?> matchPredicate = (MatchPredicate<?>) queryPredicate;
            OrPredicate orPredicate = new OrPredicateImpl();
            for (String attributeName : matchPredicate.getAttributeNames()) {
                orPredicate.getPredicates().add(new AttributePredicateImpl<>(attributeName, matchPredicate.getMatchTerm(), AttributePredicate.Operator.STARTS_WITH_IGNORE_CASE));
            }
            predicate = cb.or(handlePredicates(orPredicate.getPredicates(), binds, cb, entityRoot, entityType));
        }
        return predicate;
    }

    private <E> Predicate[] handlePredicates(@NonNull List<QueryPredicate> predicates,
            @NonNull Map<ParameterExpression, Object> binds,
            @NonNull CriteriaBuilder cb,
            @NonNull Root<E> entityRoot,
            @NonNull EntityType<E> entityType) throws KapuaException {
        Predicate[] jpaPredicates = new Predicate[predicates.size()];

        for (int i = 0; i < predicates.size(); i++) {
            jpaPredicates[i] = toPredicate(predicates.get(i), binds, cb, entityRoot, entityType);
        }
        return jpaPredicates;
    }

    private <E> Predicate handleAttributePredicate(@NonNull AttributePredicate<?> attrPred,
            @NonNull Map<ParameterExpression, Object> binds,
            @NonNull CriteriaBuilder cb,
            @NonNull Root<E> entityRoot,
            @NonNull EntityType<E> entityType)
            throws KapuaException {
        Predicate expr;
        String attrName = attrPred.getAttributeName();

        // Parse attributes
        Object attributeValue = attrPred.getAttributeValue();
        if (attributeValue instanceof KapuaId && !(attributeValue instanceof KapuaEid)) {
            attributeValue = KapuaEid.parseKapuaId((KapuaId) attributeValue);
        }

        // Fields to query properties of sub attributes of the root entity
        Attribute<?, ?> attribute;
        if (attrName.contains(ATTRIBUTE_SEPARATOR)) {
            attribute = entityType.getAttribute(attrName.split(ATTRIBUTE_SEPARATOR_ESCAPED)[0]);
        } else {
            attribute = entityType.getAttribute(attrName);
        }

        // Convert old Object[] support to List<?>
        if (attributeValue instanceof Object[]) {
            attributeValue = Arrays.asList((Object[]) attributeValue);
        }

        // Support IN clause
        if (attributeValue instanceof Collection) {
            Collection<?> attributeValues = (Collection<?>) attributeValue;

            Expression<?> orPredicate = extractAttribute(entityRoot, attrName);

            Predicate[] orPredicates = attributeValues.stream()
                    .map(value -> {
                        Object predicateValue;
                        if (value instanceof KapuaId && !(value instanceof KapuaEid)) {
                            predicateValue = KapuaEid.parseKapuaId((KapuaId) value);
                        } else {
                            predicateValue = value;
                        }

                        return cb.equal(orPredicate, predicateValue);
                    }).toArray(Predicate[]::new);

            expr = cb.and(cb.or(orPredicates));
        } else {
            final String escapedAttributeValue = Optional.ofNullable(attributeValue)
                    .map(a -> a.toString()
                            .replace(configuration.like, configuration.escape + configuration.like)
                            .replace(configuration.any, configuration.escape + configuration.any))
                    .orElse(null);
            switch (attrPred.getOperator()) {
            case LIKE:
                ParameterExpression<String> pl = cb.parameter(String.class);
                binds.put(pl, configuration.like + escapedAttributeValue + configuration.like);
                expr = cb.like(extractAttribute(entityRoot, attrName), pl);
                break;

            case LIKE_IGNORE_CASE:
                ParameterExpression<String> plci = cb.parameter(String.class);
                binds.put(plci, configuration.like + escapedAttributeValue.toLowerCase() + configuration.like);
                expr = cb.like(cb.lower(extractAttribute(entityRoot, attrName)), plci);
                break;

            case STARTS_WITH:
                ParameterExpression<String> psw = cb.parameter(String.class);
                binds.put(psw, escapedAttributeValue + configuration.like);
                expr = cb.like(extractAttribute(entityRoot, attrName), psw);
                break;

            case STARTS_WITH_IGNORE_CASE:
                ParameterExpression<String> pswci = cb.parameter(String.class);
                binds.put(pswci, escapedAttributeValue.toLowerCase() + configuration.like);
                expr = cb.like(cb.lower(extractAttribute(entityRoot, attrName)), pswci);
                break;

            case IS_NULL:
                expr = cb.isNull(extractAttribute(entityRoot, attrName));
                break;

            case NOT_NULL:
                expr = cb.isNotNull(extractAttribute(entityRoot, attrName));
                break;

            case NOT_EQUAL:
                expr = cb.notEqual(extractAttribute(entityRoot, attrName), attributeValue);
                break;

            case GREATER_THAN:
                if (attributeValue instanceof Comparable && ArrayUtils.contains(attribute.getJavaType().getInterfaces(), Comparable.class)) {
                    Comparable comparableAttrValue = (Comparable<?>) attributeValue;
                    Expression<? extends Comparable> comparableExpression = extractAttribute(entityRoot, attrName);
                    expr = cb.greaterThan(comparableExpression, comparableAttrValue);
                } else {
                    throw new KapuaException(KapuaErrorCodes.ILLEGAL_ARGUMENT, COMPARE_ERROR_MESSAGE);
                }
                break;

            case GREATER_THAN_OR_EQUAL:
                if (attributeValue instanceof Comparable && ArrayUtils.contains(attribute.getJavaType().getInterfaces(), Comparable.class)) {
                    Expression<? extends Comparable> comparableExpression = extractAttribute(entityRoot, attrName);
                    Comparable comparableAttrValue = (Comparable<?>) attributeValue;
                    expr = cb.greaterThanOrEqualTo(comparableExpression, comparableAttrValue);
                } else {
                    throw new KapuaException(KapuaErrorCodes.ILLEGAL_ARGUMENT, COMPARE_ERROR_MESSAGE);
                }
                break;

            case LESS_THAN:
                if (attributeValue instanceof Comparable && ArrayUtils.contains(attribute.getJavaType().getInterfaces(), Comparable.class)) {
                    Expression<? extends Comparable> comparableExpression = extractAttribute(entityRoot, attrName);
                    Comparable comparableAttrValue = (Comparable<?>) attributeValue;
                    expr = cb.lessThan(comparableExpression, comparableAttrValue);
                } else {
                    throw new KapuaException(KapuaErrorCodes.ILLEGAL_ARGUMENT, COMPARE_ERROR_MESSAGE);
                }
                break;
            case LESS_THAN_OR_EQUAL:
                if (attributeValue instanceof Comparable && ArrayUtils.contains(attribute.getJavaType().getInterfaces(), Comparable.class)) {
                    Expression<? extends Comparable> comparableExpression = extractAttribute(entityRoot, attrName);
                    Comparable comparableAttrValue = (Comparable<?>) attributeValue;
                    expr = cb.lessThanOrEqualTo(comparableExpression, comparableAttrValue);
                } else {
                    throw new KapuaException(KapuaErrorCodes.ILLEGAL_ARGUMENT, COMPARE_ERROR_MESSAGE);
                }
                break;

            case EQUAL:
            default:
                expr = cb.equal(extractAttribute(entityRoot, attrName), attributeValue);
            }
        }
        return expr;
    }

    /**
     * Utility method that selects the correct {@link Root} attribute.
     * <p>
     * This method handles {@link Embedded} attributes and nested entities: filter predicates take advantage of the dot notation to access them.
     *
     * @param entityRoot    The {@link Root} entity from which extract the attribute.
     * @param attributeName The full attribute name.
     * @return The {@link Path} expression that matches the given {@code attributeName} parameter.
     */
    public <E, P> Path<P> extractAttribute(@NonNull Root<E> entityRoot, @NonNull String attributeName) {
        Path<P> expressionPath;
        if (attributeName.contains(ATTRIBUTE_SEPARATOR)) {
            String[] pathComponents = attributeName.split(ATTRIBUTE_SEPARATOR_ESCAPED);
            Path<P> rootPath = entityRoot.get(pathComponents[0]);
            expressionPath = rootPath;
            for (int i = 1; i < pathComponents.length; i++) {
                expressionPath = rootPath.get(pathComponents[i]);
                rootPath = expressionPath;
            }
        } else {
            expressionPath = entityRoot.get(attributeName);
        }
        return expressionPath;
    }
}
