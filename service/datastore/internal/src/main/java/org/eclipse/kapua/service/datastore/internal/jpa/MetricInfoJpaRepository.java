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
import org.eclipse.kapua.commons.jpa.KapuaJpaRepositoryConfiguration;
import org.eclipse.kapua.service.datastore.MetricInfoFactory;
import org.eclipse.kapua.service.datastore.internal.DatastoreCacheManager;
import org.eclipse.kapua.service.datastore.internal.MetricInfoRepository;
import org.eclipse.kapua.service.datastore.internal.model.MetricInfoImpl;
import org.eclipse.kapua.service.datastore.model.MetricInfo;
import org.eclipse.kapua.service.datastore.model.MetricInfoListResult;
import org.eclipse.kapua.service.datastore.model.query.MetricInfoQuery;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedUpdate;
import org.eclipse.kapua.storage.TxManager;

import java.util.List;

/**
 * SQL ({@code dt_metric_info}) implementation of {@link MetricInfoRepository}, bound when {@code datastore.info.storage=SQL}.
 *
 * @since 2.1.0
 */
public class MetricInfoJpaRepository extends StorableJpaRepository<MetricInfo, MetricInfoImpl, MetricInfoListResult, MetricInfoQuery> implements MetricInfoRepository {

    public MetricInfoJpaRepository(MetricInfoFactory metricInfoFactory,
                            TxManager txManager,
                            KapuaJpaRepositoryConfiguration jpaRepositoryConfiguration,
                            DatastoreCacheManager datastoreCacheManager) {
        super(MetricInfoImpl.class,
                metricInfoFactory,
                txManager,
                jpaRepositoryConfiguration,
                InfoFieldMapping.METRIC,
                // the same cache the Elasticsearch repository receives (see StorableJpaRepository#itemsCache)
                datastoreCacheManager.getMetricsCache(),
                MetricInfo::getId);
    }

    @Override
    public LastPublishedResult updateLastPublished(List<LastPublishedUpdate> updates, long minAdvanceMillis) throws KapuaException {
        return doUpdateLastPublished(updates, minAdvanceMillis);
    }
}
