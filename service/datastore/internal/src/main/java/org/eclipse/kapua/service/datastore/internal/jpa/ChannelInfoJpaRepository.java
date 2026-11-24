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
import org.eclipse.kapua.service.datastore.ChannelInfoFactory;
import org.eclipse.kapua.service.datastore.internal.DatastoreCacheManager;
import org.eclipse.kapua.service.datastore.internal.ChannelInfoRepository;
import org.eclipse.kapua.service.datastore.internal.model.ChannelInfoImpl;
import org.eclipse.kapua.service.datastore.model.ChannelInfo;
import org.eclipse.kapua.service.datastore.model.ChannelInfoListResult;
import org.eclipse.kapua.service.datastore.model.query.ChannelInfoQuery;
import org.eclipse.kapua.storage.TxManager;

/**
 * SQL ({@code dt_channel_info}) implementation of {@link ChannelInfoRepository}, bound when {@code datastore.info.storage=SQL}.
 *
 * @since 2.1.0
 */
public class ChannelInfoJpaRepository extends StorableJpaRepository<ChannelInfo, ChannelInfoImpl, ChannelInfoListResult, ChannelInfoQuery> implements ChannelInfoRepository {

    public ChannelInfoJpaRepository(ChannelInfoFactory channelInfoFactory,
                            TxManager txManager,
                            KapuaJpaRepositoryConfiguration jpaRepositoryConfiguration,
                            DatastoreCacheManager datastoreCacheManager) {
        super(ChannelInfoImpl.class,
                channelInfoFactory,
                txManager,
                jpaRepositoryConfiguration,
                InfoFieldMapping.CHANNEL,
                // the same cache the Elasticsearch repository receives (see StorableJpaRepository#itemsCache)
                datastoreCacheManager.getChannelsCache(),
                ChannelInfo::getId);
    }
}
