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

import org.eclipse.kapua.service.datastore.internal.schema.ChannelInfoSchema;
import org.eclipse.kapua.service.datastore.internal.schema.ClientInfoSchema;
import org.eclipse.kapua.service.datastore.internal.schema.MetricInfoSchema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps the storable field names used by queries and sorts (the Elasticsearch field names, e.g. {@code client_id}, {@code metric.name}) to the JPA attribute
 * names of an info entity.
 *
 * @since 2.1.0
 */
public final class InfoFieldMapping {

    /**
     * The id attribute of every info entity (the derived info id, the Elasticsearch {@code _id}).
     */
    public static final String ID_ATTRIBUTE = "id";
    public static final String SCOPE_ID_ATTRIBUTE = "scopeId";

    private static final String ES_ID_FIELD = "_id";
    /**
     * The SQL tables already have the last message columns; these field names make them queryable (not mapped in Elasticsearch).
     */
    public static final String LAST_MESSAGE_ON_FIELD = "last_message_on";
    public static final String LAST_MESSAGE_ID_FIELD = "last_message_id";

    public static final InfoFieldMapping CHANNEL = new InfoFieldMapping(entries(
            ChannelInfoSchema.CHANNEL_SCOPE_ID, SCOPE_ID_ATTRIBUTE,
            ChannelInfoSchema.CHANNEL_CLIENT_ID, "clientId",
            ChannelInfoSchema.CHANNEL_NAME, "name",
            ChannelInfoSchema.CHANNEL_TIMESTAMP, "firstMessageOn",
            ChannelInfoSchema.CHANNEL_MESSAGE_ID, "firstMessageId",
            LAST_MESSAGE_ON_FIELD, "lastMessageOn",
            LAST_MESSAGE_ID_FIELD, "lastMessageId"));

    public static final InfoFieldMapping CLIENT = new InfoFieldMapping(entries(
            ClientInfoSchema.CLIENT_SCOPE_ID, SCOPE_ID_ATTRIBUTE,
            ClientInfoSchema.CLIENT_ID, "clientId",
            ClientInfoSchema.CLIENT_TIMESTAMP, "firstMessageOn",
            ClientInfoSchema.CLIENT_MESSAGE_ID, "firstMessageId",
            LAST_MESSAGE_ON_FIELD, "lastMessageOn",
            LAST_MESSAGE_ID_FIELD, "lastMessageId"));

    /**
     * Only the full {@code metric.timestamp} names the first message time: Elasticsearch metric info has no top-level {@code timestamp} field,
     * so the bare {@code timestamp} (the field of messages, channel and client infos) is not mapped, as in Elasticsearch.
     */
    public static final InfoFieldMapping METRIC = new InfoFieldMapping(entries(
            MetricInfoSchema.METRIC_SCOPE_ID, SCOPE_ID_ATTRIBUTE,
            MetricInfoSchema.METRIC_CLIENT_ID, "clientId",
            MetricInfoSchema.METRIC_CHANNEL, "channel",
            MetricInfoSchema.METRIC_MTR_NAME_FULL, "name",
            MetricInfoSchema.METRIC_MTR_TYPE_FULL, "metricType",
            MetricInfoSchema.METRIC_MTR_TIMESTAMP_FULL, "firstMessageOn",
            MetricInfoSchema.METRIC_MTR_MSG_ID_FULL, "firstMessageId",
            LAST_MESSAGE_ON_FIELD, "lastMessageOn",
            LAST_MESSAGE_ID_FIELD, "lastMessageId"));

    private final Map<String, String> attributes;

    private InfoFieldMapping(Map<String, String> attributes) {
        Map<String, String> all = new HashMap<>(attributes);
        all.put(ES_ID_FIELD, ID_ATTRIBUTE);
        all.put(ID_ATTRIBUTE, ID_ATTRIBUTE);
        this.attributes = Collections.unmodifiableMap(all);
    }

    /**
     * @param storableField the storable (Elasticsearch) field name.
     * @return the JPA attribute name.
     * @throws IllegalArgumentException if the field has no JPA attribute.
     */
    public String attributeOf(String storableField) {
        String attribute = attributes.get(storableField);
        if (attribute == null) {
            throw new IllegalArgumentException("No SQL attribute for the datastore field: " + storableField);
        }
        return attribute;
    }

    public String idAttribute() {
        return ID_ATTRIBUTE;
    }

    private static Map<String, String> entries(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
