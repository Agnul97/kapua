/*******************************************************************************
 * Copyright (c) 2016, 2026 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *     Red Hat Inc
 *******************************************************************************/
package org.eclipse.kapua.service.datastore.internal.setting;

import org.eclipse.kapua.commons.setting.SettingKey;

/**
 * Datastore setting keys.
 *
 * @since 1.0
 */
public enum DatastoreSettingsKey implements SettingKey {

    /**
     * Local cache expire time (default value is no specific cache value is defined)
     */
    CONFIG_CACHE_LOCAL_EXPIRE_AFTER("datastore.cache.local.expire.after"),
    /**
     * Channels Local cache expire time. Overrides the default value if specified, can be omitted to use the value of CONFIG_CACHE_LOCAL_EXPIRE_AFTER.
     */
    CONFIG_CHANNELS_CACHE_LOCAL_EXPIRE_AFTER("datastore.cache.channels.local.expire.after"),
    /**
     * Clients Local cache expire time. Overrides the default value if specified, can be omitted to use the value of CONFIG_CACHE_LOCAL_EXPIRE_AFTER.
     */
    CONFIG_CLIENTS_CACHE_LOCAL_EXPIRE_AFTER("datastore.cache.clients.local.expire.after"),
    /**
     * Metrics Local cache expire time. Overrides the default value if specified, can be omitted to use the value of CONFIG_CACHE_LOCAL_EXPIRE_AFTER.
     */
    CONFIG_METRICS_CACHE_LOCAL_EXPIRE_AFTER("datastore.cache.metrics.local.expire.after"),
    /**
     * Channels Local cache expire strategy (either MODIFIED or TOUCHED). If omitted, MODIFIED will be assumed.
     */
    CONFIG_CHANNELS_CACHE_LOCAL_EXPIRE_STRATEGY("datastore.cache.channels.local.expire.strategy"),
    /**
     * Clients Local cache expire strategy (either MODIFIED or TOUCHED). If omitted, MODIFIED will be assumed.
     */
    CONFIG_CLIENTS_CACHE_LOCAL_EXPIRE_STRATEGY("datastore.cache.clients.local.expire.strategy"),
    /**
     * Metrics Local cache expire strategy (either MODIFIED or TOUCHED). If omitted, MODIFIED will be assumed.
     */
    CONFIG_METRICS_CACHE_LOCAL_EXPIRE_STRATEGY("datastore.cache.metrics.local.expire.strategy"),
    /**
     * Local cache maximum size (default value is no specific cache value is defined)
     */
    CONFIG_CACHE_LOCAL_SIZE_MAXIMUM("datastore.cache.local.size.maximum"),
    /**
     * Channels Local cache expire time. Overrides the default value if specified, can be omitted to use the value of CONFIG_CACHE_LOCAL_SIZE_MAXIMUM.
     */
    CONFIG_CHANNELS_CACHE_LOCAL_SIZE_MAXIMUM("datastore.cache.channels.local.size.maximum"),
    /**
     * Clients Local cache expire time. Overrides the default value if specified, can be omitted to use the value of CONFIG_CACHE_LOCAL_SIZE_MAXIMUM.
     */
    CONFIG_CLIENTS_CACHE_LOCAL_SIZE_MAXIMUM("datastore.cache.clients.local.size.maximum"),
    /**
     * Metrics Local cache expire time. Overrides the default value if specified, can be omitted to use the value of CONFIG_CACHE_LOCAL_SIZE_MAXIMUM.
     */
    CONFIG_METRICS_CACHE_LOCAL_SIZE_MAXIMUM("datastore.cache.metrics.local.size.maximum"),
    /**
     * Metadata cache maximum size (default value is no specific cache value is defined)
     */
    CONFIG_CACHE_METADATA_LOCAL_SIZE_MAXIMUM("datastore.cache.metadata.local.size.maximum"),
    /**
     * Enable datastore timing profile
     */
    CONFIG_DATA_STORAGE_ENABLE_TIMING_PROFILE("datastore.enableTimingProfile"),
    /**
     * Datastore timing profile threshold
     */
    CONFIG_DATA_STORAGE_TIMING_PROFILE_THRESHOLD("datastore.timingProfileThreshold"),
    /**
     * Elasticsearch index refresh interval (the data is available for a search operation only if it is indexed)
     */
    INDEX_REFRESH_INTERVAL("datastore.index.refresh_interval"),
    /**
     * Shards number
     */
    INDEX_SHARD_NUMBER("datastore.index.number_of_shards"),
    /**
     * Replicas count
     */
    INDEX_REPLICA_NUMBER("datastore.index.number_of_replicas"),
    /**
     * Elasticsearch index refresh interval (the data is available for a search operation only if it is indexed)
     */
    CONFIG_MAX_ENTRIES_ON_DELETE("datastore.delete.max_entries_on_delete"),
    /**
     * Elasticsearch index name system-wide prefix
     */
    INDEX_PREFIX("datastore.index.prefix"),
    /**
     * Elasticsearch index width. Allowed values: "week", "day", "hour"
     */
    INDEXING_WINDOW_OPTION("datastore.index.window"),
    /**
     * Disables the entire Datastore feature
     */
    DISABLE_DATASTORE("datastore.disable"),
    /**
     * Elasticsearch limit+offset maximum value
     */
    MAX_RESULT_WINDOW_VALUE("datastore.max_result_window"),
    /**
     * Datastore service configurations local cache max size
     */
    CONFIG_CACHE_CONFIGURATION_LOCAL_SIZE_MAXIMUM("datastore.cache.configuration.local.size.maximum"),
    /**
     * Datastore service configurations local cache expiration time
     */
    CONFIG_CACHE_CONFIGURATION_LOCAL_EXPIRE_AFTER("datastore.cache.configuration.local.expire.after"),
    /**
     * Elasticsearch max number of field mappings for the message index
     */
    CONFIG_MESSAGES_INDEX_MAX_INDEXED_METRICS("datastore.index.message.maxIndexedMetrics"),
    /**
     * Turns on the channel last published tracker (recording and flushing of last_message_on / last_message_id on the channel info docs).
     *
     * @since 2.1.0
     */
    LAST_PUBLISHED_CHANNEL_ENABLED("datastore.last_published.channel.enabled"),
    /**
     * Smallest advance (seconds) of a channel's last message worth writing (N): each channel is written at most once per N.
     * Must stay well below the smallest message TTL.
     *
     * @since 2.1.0
     */
    LAST_PUBLISHED_CHANNEL_MIN_ADVANCE("datastore.last_published.channel.min_advance"),
    /**
     * How often (seconds) the channel last published queue is flushed.
     *
     * @since 2.1.0
     */
    LAST_PUBLISHED_CHANNEL_FLUSH_INTERVAL("datastore.last_published.channel.flush_interval"),
    /**
     * Extra delay (seconds) before the first channel flush: gives each tracker its own offset so their flushes do not hit the store together.
     *
     * @since 2.1.0
     */
    LAST_PUBLISHED_CHANNEL_INITIAL_DELAY("datastore.last_published.channel.initial_delay"),
    /**
     * Channel updates per bulk request of a last published flush.
     *
     * @since 2.1.0
     */
    LAST_PUBLISHED_CHANNEL_BULK_SIZE("datastore.last_published.channel.bulk_size"),
    /**
     * Maximum number of channel keys remembered as already written (the in-memory filter). An evicted key only costs one extra, usually noop, write.
     *
     * @since 2.1.0
     */
    LAST_PUBLISHED_CHANNEL_LAST_FLUSHED_MAX_SIZE("datastore.last_published.channel.last_flushed.max_size"),

    /**
     * Where the info docs (channel, client, metric) are stored: {@code ES} (Elasticsearch, default) or {@code SQL} (the {@code dt_*_info} tables).
     *
     * @since 2.1.0
     */
    INFO_STORAGE("datastore.info.storage");

    private String key;

    private DatastoreSettingsKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
