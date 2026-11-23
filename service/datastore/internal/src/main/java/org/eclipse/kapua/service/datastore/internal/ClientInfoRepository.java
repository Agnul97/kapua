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
package org.eclipse.kapua.service.datastore.internal;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedStore;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedUpdate;
import org.eclipse.kapua.service.datastore.model.ClientInfo;
import org.eclipse.kapua.service.datastore.model.ClientInfoListResult;
import org.eclipse.kapua.service.datastore.model.query.ClientInfoQuery;
import org.eclipse.kapua.service.storable.repository.StorableRepository;

import java.util.List;

public interface ClientInfoRepository extends StorableRepository<ClientInfo, ClientInfoListResult, ClientInfoQuery> {

    /**
     * Moves {@code last_message_on} / {@code last_message_id} forward on EXISTING items, with the contract of {@link LastPublishedStore#record}
     * (never creates an item, a missing one is reported as MISSING). Called by the last published tracker only.
     *
     * @param updates          the updates of this item type, at most one per key.
     * @param minAdvanceMillis the smallest advance worth writing.
     * @return the outcome per update.
     * @throws KapuaException when the whole call failed: the caller retries every update.
     * @since 2.1.0
     */
    LastPublishedResult updateLastPublished(List<LastPublishedUpdate> updates, long minAdvanceMillis) throws KapuaException;
}
