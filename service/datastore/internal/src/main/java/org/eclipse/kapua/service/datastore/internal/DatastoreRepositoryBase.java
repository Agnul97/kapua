/*******************************************************************************
 * Copyright (c) 2016, 2022 Eurotech and/or its affiliates and others
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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.cache.LocalCache;
import org.eclipse.kapua.commons.util.KapuaDateUtils;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult.Outcome;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedUpdate;
import org.eclipse.kapua.service.datastore.internal.schema.ChannelInfoSchema;
import org.eclipse.kapua.service.datastore.internal.schema.ClientInfoSchema;
import org.eclipse.kapua.service.datastore.internal.schema.MetricInfoSchema;
import org.eclipse.kapua.service.datastore.internal.setting.DatastoreSettings;
import org.eclipse.kapua.service.datastore.internal.setting.DatastoreSettingsKey;
import org.eclipse.kapua.service.storeengine.client.StoreEngineClientProvider;
import org.eclipse.kapua.service.storeengine.client.DeviceStoreRepository;
import org.eclipse.kapua.service.storeengine.client.SchemaKeys;
import org.eclipse.kapua.service.storeengine.client.model.BulkUpdateRequest;
import org.eclipse.kapua.service.storeengine.client.model.BulkUpdateResponse;
import org.eclipse.kapua.service.storeengine.client.model.UpdateRequest;
import org.eclipse.kapua.service.storeengine.client.model.UpdateResponse;
import org.eclipse.kapua.service.storable.StorableFactory;
import org.eclipse.kapua.service.storable.exception.MappingException;
import org.eclipse.kapua.service.storable.model.Storable;
import org.eclipse.kapua.service.storable.model.StorableListResult;
import org.eclipse.kapua.service.storable.model.query.StorableQuery;
import org.eclipse.kapua.service.storable.model.query.predicate.StorablePredicateFactory;
import org.eclipse.kapua.service.storable.model.utils.KeyValueEntry;
import org.eclipse.kapua.service.storable.model.utils.MappingUtils;
import org.eclipse.kapua.service.storable.repository.StorableRepository;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class DatastoreRepositoryBase<
        T extends Storable,
        L extends StorableListResult<T>,
        Q extends StorableQuery>
        extends DeviceStoreRepository<T, L, Q>
        implements StorableRepository<T, L, Q> {

    protected final DatastoreSettings datastoreSettings;
    // If new pre-defined mappings are added in {@link MessageSchema}, update the following value
    // consistently. Refer to Elasticsearch documentation to find how many mappings need to be
    // allocated for the new fields based upon their type
    private static final int PREDEFINED_FIELD_MAPPINGS_COUNT = 23;

    /**
     * The three info schemas use the same last message field names, so one request shape serves all of them.
     */
    static final String LAST_MESSAGE_ON = ChannelInfoSchema.CHANNEL_LAST_MESSAGE_ON;
    static final String LAST_MESSAGE_ID = ChannelInfoSchema.CHANNEL_LAST_MESSAGE_ID;

    static {
        if (!LAST_MESSAGE_ON.equals(ClientInfoSchema.CLIENT_LAST_MESSAGE_ON) || !LAST_MESSAGE_ON.equals(MetricInfoSchema.METRIC_LAST_MESSAGE_ON)
                || !LAST_MESSAGE_ID.equals(ClientInfoSchema.CLIENT_LAST_MESSAGE_ID) || !LAST_MESSAGE_ID.equals(MetricInfoSchema.METRIC_LAST_MESSAGE_ID)) {
            throw new IllegalStateException("The info schemas must share the last message field names");
        }
    }

    protected DatastoreRepositoryBase(
            StoreEngineClientProvider storeEngineClientProviderInstance,
            Class<T> clazz,
            StorableFactory<T, L, Q> storableFactory,
            StorablePredicateFactory storablePredicateFactory,
            LocalCache<String, Boolean> indexesCache,
            DatastoreSettings datastoreSettings) {
        super(storeEngineClientProviderInstance, clazz, storableFactory, storablePredicateFactory,
                indexesCache);
        this.datastoreSettings = datastoreSettings;
    }

    protected DatastoreRepositoryBase(
            StoreEngineClientProvider storeEngineClientProviderInstance,
            Class<T> clazz,
            StorableFactory<T, L, Q> storableFactory,
            StorablePredicateFactory storablePredicateFactory, DatastoreSettings datastoreSettings) {
        super(storeEngineClientProviderInstance, clazz, storableFactory, storablePredicateFactory);
        this.datastoreSettings = datastoreSettings;
    }

    /**
     * @param idxName
     * @return
     * @throws org.eclipse.kapua.service.storable.exception.MappingException
     * @since 1.0.0
     */
    @Override
    protected ObjectNode getMappingSchema(String idxName) throws MappingException {
        String idxRefreshInterval = String.format("%ss", datastoreSettings.getLong(DatastoreSettingsKey.INDEX_REFRESH_INTERVAL));
        Integer idxShardNumber = datastoreSettings.getInt(DatastoreSettingsKey.INDEX_SHARD_NUMBER, 1);
        Integer idxReplicaNumber = datastoreSettings.getInt(DatastoreSettingsKey.INDEX_REPLICA_NUMBER, 0);
        Optional<Integer> maxIndexedMetrics = datastoreSettings.getInteger(DatastoreSettingsKey.CONFIG_MESSAGES_INDEX_MAX_INDEXED_METRICS);
        if (maxIndexedMetrics.isPresent() && maxIndexedMetrics.get() <= 0) {
            maxIndexedMetrics = Optional.of(0);
        }

        ObjectNode rootNode = MappingUtils.newObjectNode();
        ObjectNode settingsNode = MappingUtils.newObjectNode();
        ObjectNode refreshIntervalNode = MappingUtils.newObjectNode(new KeyValueEntry[]{
                new KeyValueEntry(SchemaKeys.KEY_REFRESH_INTERVAL, idxRefreshInterval),
                new KeyValueEntry(SchemaKeys.KEY_SHARD_NUMBER, idxShardNumber),
                new KeyValueEntry(SchemaKeys.KEY_REPLICA_NUMBER, idxReplicaNumber)});
        if (maxIndexedMetrics.isPresent()) {
            // PREDEFINED_FIELD_MAPPINGS mappings are allocated to predefined indexed fields (e.g. the topic), then each metric takes
            // two other mappings. One for the metric itself and one more for the type (e.g. {"temp": {"type":"dbl"}})
            int totalRequiredMappings = PREDEFINED_FIELD_MAPPINGS_COUNT + maxIndexedMetrics.get()*2;
            MappingUtils.appendField(refreshIntervalNode, SchemaKeys.KEY_MAPPING_TOTAL_FIELDS_LIMIT, totalRequiredMappings);
        }
        settingsNode.set(SchemaKeys.KEY_INDEX, refreshIntervalNode);
        rootNode.set(SchemaKeys.KEY_SETTINGS, settingsNode);
        return rootNode;
    }

    /**
     * Elasticsearch implementation of {@code updateLastPublished} for the info repositories: one bulk of partial updates on the index of each
     * update's scope ({@link #indexResolver}), sent through the client's existing bulk upsert with {@link UpdateRequest#partial} requests (only the two
     * fields, no {@code doc_as_upsert}).
     * <p>
     * Deliberately NOT through {@code synchIndex}: an update must never create an index or a doc (a missing one answers 404 → MISSING), and
     * {@code synchIndex} reads and writes {@link #indexUpserted}, which for the info repositories is the facade's info cache.
     * <p>
     * TODO: for now a PLAIN partial update (last write wins), to measure the write cost in a stress test. For correctness it must be replaced by the
     * scripted update of the design doc (step 5: Painless max compare + minimum advance, {@code ctx.op = 'noop'} otherwise, sent inline; the client
     * needs a script variant of {@link UpdateRequest#partial} for it). Without it the stored value can move BACKWARDS (two consumers flushing
     * different times for the same item, a restarted consumer whose filter is empty, late device data), {@code minAdvanceMillis} is ignored, and
     * redundant writes are reindexed instead of answering noop. The stored value stays a lower bound (it only moves to an older real message), so
     * nothing is wrongly deleted, but it is less precise than it could be.
     *
     * @since 2.1.0
     */
    protected LastPublishedResult doUpdateLastPublished(List<LastPublishedUpdate> updates, long minAdvanceMillis) throws KapuaException {
        LastPublishedResult result = new LastPublishedResult();
        if (updates.isEmpty()) {
            return result;
        }
        BulkUpdateRequest bulkRequest = new BulkUpdateRequest();
        for (LastPublishedUpdate u : updates) {
            // TODO: correctness - minAdvanceMillis is ignored by the plain update (see above)
            bulkRequest.add(toLastPublishedRequest(indexResolver(u.getKey().getScopeId()), u));
        }

        // A request-level failure (ES unreachable, whole bulk rejected) propagates: the tracker requeues everything.
        List<UpdateResponse> responses;
        try {
            BulkUpdateResponse bulkResponse = storeEngineClientProviderInstance.getStoreEngineClient().upsert(bulkRequest);
            responses = bulkResponse.getResponse();
        } catch (Exception e) {
            throw KapuaException.internalError(e, "Last published bulk update failed");
        }
        // ES answers one item per action, in the request order, so the i-th response belongs to the i-th update.
        // A different count means the positions can't be trusted: fail the whole call, the tracker requeues everything.
        if (responses.size() != updates.size()) {
            throw KapuaException.internalError(String.format("Last published bulk response has %s items, %s updates were sent", responses.size(), updates.size()));
        }

        for (int i = 0; i < responses.size(); i++) {
            UpdateResponse response = responses.get(i);
            Outcome outcome = toLastPublishedOutcome(response);
            if (outcome == Outcome.FAILED) {
                logger.warn("Last published update failed permanently: {}/{} status {}, reason: {}", response.getIndex(), response.getId(), response.getStatus(), response.getDescription());
            }
            result.put(updates.get(i).getKey(), outcome);
        }
        return result;
    }

    /**
     * The partial update of one item: both fields together, never an upsert.
     */
    static UpdateRequest toLastPublishedRequest(String indexName, LastPublishedUpdate update) throws KapuaException {
        Map<String, Object> doc = new HashMap<>();
        try {
            // same format as every other datastore date (DATASTORE_DATE_FORMAT), so the field mapping accepts it
            doc.put(LAST_MESSAGE_ON, KapuaDateUtils.formatDate(new Date(update.getOnMillis())));
        } catch (ParseException e) {
            throw KapuaException.internalError(e, "Cannot format the last message date");
        }
        doc.put(LAST_MESSAGE_ID, update.getMessageId());
        return UpdateRequest.partial(update.getKey().getId(), indexName, doc);
    }

    /**
     * Maps one bulk item to an {@link Outcome}:
     * <ul>
     *     <li>2xx ({@code updated} or {@code noop}) → DONE;</li>
     *     <li>404 (missing doc, or missing index) → MISSING: the update has no upsert, so nothing was created;</li>
     *     <li>409 (version conflict: another writer updated the doc at the same moment; no {@code retry_on_conflict} is sent, so the tracker rewrites it at the
     *     next flush and the conflicts stay visible in its retry count), 429 (push-back), 5xx → RETRY;</li>
     *     <li>any other status (e.g. 400 for a mapping error) → FAILED: retrying would fail the same way.</li>
     * </ul>
     */
    static Outcome toLastPublishedOutcome(UpdateResponse response) {
        int status = response.getStatus();
        if (status >= 200 && status < 300) {
            return Outcome.DONE;
        }
        if (status == 404) {
            return Outcome.MISSING;
        }
        if (status == 409 || status == 429 || status >= 500) {
            return Outcome.RETRY;
        }
        return Outcome.FAILED;
    }
}
