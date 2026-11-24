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

import org.eclipse.kapua.commons.jpa.KapuaJpaRepositoryConfiguration;
import org.eclipse.kapua.service.datastore.ClientInfoFactory;
import org.eclipse.kapua.service.datastore.internal.DatastoreCacheManager;
import org.eclipse.kapua.service.datastore.internal.ClientInfoRepository;
import org.eclipse.kapua.service.datastore.internal.model.ClientInfoImpl;
import org.eclipse.kapua.service.datastore.model.ClientInfo;
import org.eclipse.kapua.service.datastore.model.ClientInfoListResult;
import org.eclipse.kapua.service.datastore.model.query.ClientInfoQuery;
import org.eclipse.kapua.storage.TxManager;

/**
 * SQL ({@code dt_client_info}) implementation of {@link ClientInfoRepository}, bound when {@code datastore.info.storage=SQL}.
 *
 * @since 2.1.0
 */
public class ClientInfoJpaRepository extends StorableJpaRepository<ClientInfo, ClientInfoImpl, ClientInfoListResult, ClientInfoQuery> implements ClientInfoRepository {

    public ClientInfoJpaRepository(ClientInfoFactory clientInfoFactory,
                            TxManager txManager,
                            KapuaJpaRepositoryConfiguration jpaRepositoryConfiguration,
                            DatastoreCacheManager datastoreCacheManager) {
        super(ClientInfoImpl.class,
                clientInfoFactory,
                txManager,
                jpaRepositoryConfiguration,
                InfoFieldMapping.CLIENT,
                // the same cache the Elasticsearch repository receives (see StorableJpaRepository#itemsCache)
                datastoreCacheManager.getClientsCache(),
                ClientInfo::getId);
    }
}
