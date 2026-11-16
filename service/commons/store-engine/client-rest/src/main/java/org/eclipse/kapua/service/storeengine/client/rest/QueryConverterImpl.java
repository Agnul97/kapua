/*******************************************************************************
 * Copyright (c) 2017, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.service.storeengine.client.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.kapua.service.storeengine.client.QueryConverter;
import org.eclipse.kapua.service.storeengine.client.SchemaKeys;
import org.eclipse.kapua.service.storeengine.client.exception.QueryMappingException;
import org.eclipse.kapua.service.storable.exception.MappingException;
import org.eclipse.kapua.service.storable.model.query.SortField;
import org.eclipse.kapua.service.storable.model.query.StorableQuery;
import org.eclipse.kapua.service.storable.model.utils.MappingUtils;

import java.util.List;

/**
 * Query converter implementation that converts only fields supported by the given ES endpoint/action (Query, Delete, Count etc.)
 * In order to avoid bad requests
 *
 * @since 2.1.0
 */
public class QueryConverterImpl implements QueryConverter {

    private static ConvertOptions convertOptionsCount = new ConvertOptions(false, true, false, false, false);
    private static ConvertOptions convertOptionsDelete = convertOptionsCount;
    private static ConvertOptions convertOptionsSearch = new ConvertOptions(true, true, true, true, true);

    @Override
    public JsonNode convertQuery(Object query) throws QueryMappingException {
        return convertQuery(query, convertOptionsSearch);
    }

    @Override
    public JsonNode convertCountQuery(Object query) throws QueryMappingException {
        return convertQuery(query, convertOptionsCount);
    }

    @Override
    public JsonNode convertDeleteQuery(Object query) throws QueryMappingException {
        return convertQuery(query, convertOptionsDelete);
    }

    private JsonNode convertQuery(Object query, ConvertOptions convertOptions) throws QueryMappingException {
        if (!(query instanceof StorableQuery)) {
            throw new QueryMappingException("Given query is not a StorableQuery");
        }

        try {
            StorableQuery storableQuery = (StorableQuery) query;
            ObjectNode rootNode = MappingUtils.newObjectNode();

            if (convertOptions.sourceEnabled) {
                ObjectNode includesFields = MappingUtils.newObjectNode();
                includesFields.set(SchemaKeys.KEY_INCLUDES, MappingUtils.newArrayNode(storableQuery.getIncludes(storableQuery.getFetchStyle())));
                includesFields.set(SchemaKeys.KEY_EXCLUDES, MappingUtils.newArrayNode(storableQuery.getExcludes(storableQuery.getFetchStyle())));
                rootNode.set(SchemaKeys.KEY_SOURCE, includesFields);
            }

            // query
            if (convertOptions.queryEnabled && storableQuery.getPredicate() != null) {
                rootNode.set(SchemaKeys.KEY_QUERY, storableQuery.getPredicate().toSerializedMap());
            }

            // sort
            ArrayNode sortNode = MappingUtils.newArrayNode();
            List<SortField> sortFields = storableQuery.getSortFields();
            if (convertOptions.sortEnabled && sortFields != null && !sortFields.isEmpty()) {
                for (SortField field : sortFields) {
                    sortNode.add(MappingUtils.newObjectNode(field.getField(), field.getSortDirection().name()));
                }
                rootNode.set(SchemaKeys.KEY_SORT, sortNode);
            }
            appendSearchAfter(rootNode, storableQuery, convertOptions.sortEnabled);

            // offset and limit settings
            Integer offset = storableQuery.getOffset();
            if (convertOptions.fromEnabled && offset != null) {
                rootNode.set(SchemaKeys.KEY_FROM, MappingUtils.newNumericNode(offset));
            }
            Integer limit = storableQuery.getLimit();
            if (convertOptions.sizeEnabled && limit != null) {
                rootNode.set(SchemaKeys.KEY_SIZE, MappingUtils.newNumericNode(limit));
            }
            return rootNode;
        } catch (MappingException me) {
            throw new QueryMappingException(me, "Cannot convert Storable Query");
        }
    }

    @Override
    public Object getFetchStyle(Object query) throws QueryMappingException {
        if (!(query instanceof StorableQuery)) {
            throw new QueryMappingException("Given query is not a StorableQuery");
        }

        return ((StorableQuery) query).getFetchStyle();
    }

    /**
     * Appends the {@code search_after} node to the given request, if requested by {@link StorableQuery#getSearchAfter()}.
     * <p>
     * It is converted only when the sort is enabled by the {@link ConvertOptions} (i.e. search requests), so count and delete conversions ignore it.
     * <p>
     * ES rejects {@code search_after} used together with {@code from > 0}: instead of silently dropping the offset, this fails fast if {@link StorableQuery#getOffset()} is greater than 0.
     * It also fails fast if there is no sort, since {@code search_after} is meaningless (and rejected by ES) without it.
     * <p>
     * TODO: once the ES version is bumped to &gt;= 7.10, a point-in-time (PIT) should be opened and passed together with {@code search_after}, to have a consistent view across pages
     * (and {@code _shard_doc} can then serve as the tiebreaker sort field). PIT is not available in ES 7.8.1, and scroll is not used on purpose.
     *
     * @param rootNode   The request node.
     * @param query      The {@link StorableQuery} to convert.
     * @param sortEnabled Whether the sort is enabled for the current conversion.
     * @throws QueryMappingException if {@code search_after} is requested without sort, or with an offset greater than 0.
     */
    private void appendSearchAfter(ObjectNode rootNode, StorableQuery query, boolean sortEnabled) throws QueryMappingException {
        List<Object> searchAfter = query.getSearchAfter();
        if (!sortEnabled || searchAfter == null || searchAfter.isEmpty()) {
            return;
        }
        if (query.getSortFields() == null || query.getSortFields().isEmpty()) {
            throw new QueryMappingException("search_after requires a sort, but the query has no sort fields");
        }
        if (query.getOffset() != null && query.getOffset() > 0) {
            throw new QueryMappingException("search_after cannot be used with an offset greater than 0, but the query has offset " + query.getOffset());
        }
        ArrayNode searchAfterNode = MappingUtils.newArrayNode();
        for (Object value : searchAfter) {
            // keep the JSON type of each value (numbers as numbers, keywords as strings): they must be passed back to ES exactly as it returned them
            if (value == null) {
                searchAfterNode.addNull();
            } else if (value instanceof Boolean) {
                searchAfterNode.add((Boolean) value);
            } else if (value instanceof Long) {
                searchAfterNode.add((Long) value);
            } else if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
                searchAfterNode.add(((Number) value).intValue());
            } else if (value instanceof Double || value instanceof Float) {
                searchAfterNode.add(((Number) value).doubleValue());
            } else if (value instanceof Number) {
                searchAfterNode.add(new java.math.BigDecimal(value.toString()));
            } else {
                searchAfterNode.add(value.toString());
            }
        }
        rootNode.set(SchemaKeys.KEY_SEARCH_AFTER, searchAfterNode);
    }

    private static class ConvertOptions {

        protected boolean sourceEnabled;
        protected boolean queryEnabled;
        protected boolean sortEnabled;
        protected boolean fromEnabled;
        protected boolean sizeEnabled;

        ConvertOptions(boolean source,boolean query,boolean sort, boolean from, boolean size) {
            this.sourceEnabled = source;
            this.queryEnabled = query;
            this.sortEnabled = sort;
            this.fromEnabled = from;
            this.sizeEnabled = size;
        }

    }

}
