/*******************************************************************************
 * Copyright (c) 2020, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.service.storable.repository;

import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.storable.model.Storable;
import org.eclipse.kapua.service.storable.model.StorableListResult;
import org.eclipse.kapua.service.storable.model.id.StorableId;
import org.eclipse.kapua.service.storable.model.query.StorableQuery;

import java.util.List;
import java.util.Set;

public interface StorableRepository<
        T extends Storable,
        L extends StorableListResult<T>,
        Q extends StorableQuery> {
    String upsert(String itemId, T item);

    Set<String> upsert(List<T> items);

    T find(KapuaId scopeId, StorableId id);

    /**
     * Queries the items.
     * <p>
     * Contract: when the query has no limit ({@link StorableQuery#getLimit()} is {@code null}) at most 10 items are returned, the default of
     * Elasticsearch search requests. The Elasticsearch implementation honours it by not sending any size; other implementations must apply it
     * explicitly (e.g. {@code StorableJpaRepository.DEFAULT_QUERY_LIMIT}). Callers that need more items set the limit.
     *
     * @param query the query.
     * @return the matching items, at most the query limit (10 when the limit is {@code null}).
     */
    L query(Q query);

    long count(Q query);

    void delete(KapuaId scopeId, StorableId id);

    void delete(Q query);

    void refreshAllIndexes();

    void refreshIndex(String indexExp);

    void deleteAllIndexes();

    void deleteIndexes(String indexExp);

}
