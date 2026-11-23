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

import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedStore;
import org.eclipse.kapua.service.datastore.model.ClientInfo;
import org.eclipse.kapua.service.datastore.model.ClientInfoListResult;
import org.eclipse.kapua.service.datastore.model.query.ClientInfoQuery;
import org.eclipse.kapua.service.storable.repository.StorableRepository;

/**
 * Also a {@link LastPublishedStore}: the last published tracker of this item type writes through the repository, so the binding chosen here
 * (Elasticsearch today, SQL later) is the storage the tracker writes to.
 */
public interface ClientInfoRepository extends StorableRepository<ClientInfo, ClientInfoListResult, ClientInfoQuery>, LastPublishedStore {
}
