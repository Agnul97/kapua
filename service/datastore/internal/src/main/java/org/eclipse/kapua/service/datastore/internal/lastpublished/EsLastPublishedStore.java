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
package org.eclipse.kapua.service.datastore.internal.lastpublished;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.util.KapuaDateUtils;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult.Outcome;
import org.eclipse.kapua.service.datastore.internal.mediator.DatastoreUtils;
import org.eclipse.kapua.service.datastore.internal.schema.ChannelInfoSchema;
import org.eclipse.kapua.service.datastore.internal.schema.ClientInfoSchema;
import org.eclipse.kapua.service.datastore.internal.schema.MetricInfoSchema;
import org.eclipse.kapua.service.storeengine.client.StoreEngineClientProvider;
import org.eclipse.kapua.service.storeengine.client.model.PartialUpdateRequest;
import org.eclipse.kapua.service.storeengine.client.model.PartialUpdateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link LastPublishedStore} writing {@code last_message_on} / {@code last_message_id} on the Elasticsearch info docs
 * ({@code <scope>-data-channel}, {@code -client}, {@code -metric}) with one bulk of partial updates per call.
 * <p>
 * TODO: for now a PLAIN partial update (last write wins), to measure the write cost in a stress test. For correctness it must be replaced by the
 * scripted update of the design doc (step 5: Painless max compare + minimum advance, {@code ctx.op = 'noop'} otherwise, sent inline; the client
 * needs a script variant of {@link PartialUpdateRequest} for it). Without it the stored value can move BACKWARDS (two consumers flushing different
 * times for the same item, a restarted consumer whose filter is empty, late device data), {@code minAdvanceMillis} is ignored, and redundant writes
 * are reindexed instead of answering noop. The stored value stays a lower bound (it only moves to an older real message), so nothing is wrongly
 * deleted, but it is less precise than it could be.
 * <p>
 * It talks to the client directly, NOT through the info repositories: those go through {@code synchIndex}, which reads and writes the info caches, and the
 * tracker must never touch them (see the design doc, Invariants).
 *
 * @since 2.1.0
 */
public class EsLastPublishedStore implements LastPublishedStore {

    private static final Logger LOG = LoggerFactory.getLogger(EsLastPublishedStore.class);

    /**
     * The three info schemas use the same field names, so one request shape serves all of them.
     */
    static final String LAST_MESSAGE_ON = ChannelInfoSchema.CHANNEL_LAST_MESSAGE_ON;
    static final String LAST_MESSAGE_ID = ChannelInfoSchema.CHANNEL_LAST_MESSAGE_ID;

    static {
        if (!LAST_MESSAGE_ON.equals(ClientInfoSchema.CLIENT_LAST_MESSAGE_ON) || !LAST_MESSAGE_ON.equals(MetricInfoSchema.METRIC_LAST_MESSAGE_ON)
                || !LAST_MESSAGE_ID.equals(ClientInfoSchema.CLIENT_LAST_MESSAGE_ID) || !LAST_MESSAGE_ID.equals(MetricInfoSchema.METRIC_LAST_MESSAGE_ID)) {
            throw new IllegalStateException("The info schemas must share the last message field names");
        }
    }

    /**
     * Concurrent updates of the same doc (several consumers, or the facade upserting it) are retried by ES instead of failing with 409.
     */
    static final int RETRY_ON_CONFLICT = 3;

    private final StoreEngineClientProvider storeEngineClientProvider;
    private final DatastoreUtils datastoreUtils;

    @Inject
    public EsLastPublishedStore(StoreEngineClientProvider storeEngineClientProvider, DatastoreUtils datastoreUtils) {
        this.storeEngineClientProvider = storeEngineClientProvider;
        this.datastoreUtils = datastoreUtils;
    }

    @Override
    public LastPublishedResult record(List<LastPublishedUpdate> updates, long minAdvanceMillis) throws KapuaException {
        LastPublishedResult result = new LastPublishedResult();
        if (updates.isEmpty()) {
            return result;
        }
        List<PartialUpdateRequest> requests = new ArrayList<>(updates.size());
        for (LastPublishedUpdate u : updates) {
            requests.add(toRequest(u, minAdvanceMillis));
        }

        // A request-level failure (ES unreachable, whole bulk rejected) propagates: the tracker requeues everything.
        List<PartialUpdateResponse> responses;
        try {
            responses = storeEngineClientProvider.getStoreEngineClient().partialUpdate(requests);
        } catch (Exception e) {
            throw KapuaException.internalError(e, "Last published bulk update failed");
        }

        // One response per request, in the request order: the client rejects a response with a different item count (it will throw an exception).
        for (int i = 0; i < responses.size(); i++) {
            PartialUpdateResponse response = responses.get(i);
            Outcome outcome = toOutcome(response);
            if (outcome == Outcome.FAILED) {
                LOG.warn("Last published update failed permanently: {}, reason: {}", response, response.getErrorReason());
            }
            result.put(updates.get(i).getKey(), outcome);
        }
        return result;
    }

    PartialUpdateRequest toRequest(LastPublishedUpdate update, long minAdvanceMillis) throws KapuaException {
        // TODO: correctness - replace with the scripted update (see the class javadoc). The plain update below ignores minAdvanceMillis
        //  and may move the stored value backwards; kept only for the stress test.
        Map<String, Object> doc = new HashMap<>();
        doc.put(LAST_MESSAGE_ON, formatOn(update));
        doc.put(LAST_MESSAGE_ID, update.getMessageId());
        return new PartialUpdateRequest(indexName(update.getKey()), update.getKey().getId(), doc, RETRY_ON_CONFLICT);
    }

    private static String formatOn(LastPublishedUpdate update) throws KapuaException {
        try {
            // same format as every other datastore date (DATASTORE_DATE_FORMAT), so the field mapping accepts it
            return KapuaDateUtils.formatDate(new Date(update.getOnMillis()));
        } catch (ParseException e) {
            throw KapuaException.internalError(e, "Cannot format the last message date");
        }
    }

    private String indexName(LastPublishedKey key) {
        switch (key.getType()) {
            case CHANNEL:
                return datastoreUtils.getChannelIndexName(key.getScopeId());
            case CLIENT:
                return datastoreUtils.getClientIndexName(key.getScopeId());
            case METRIC:
                return datastoreUtils.getMetricIndexName(key.getScopeId());
            default:
                throw new IllegalArgumentException("Unknown info type " + key.getType());
        }
    }

    /**
     * Maps one bulk item to an {@link Outcome}:
     * <ul>
     *     <li>2xx ({@code updated} or {@code noop}) → DONE;</li>
     *     <li>404 (missing doc, or missing index) → MISSING: the update has no upsert, so nothing was created;</li>
     *     <li>409 (still conflicting after {@link #RETRY_ON_CONFLICT}), 429 (push-back), 5xx → RETRY;</li>
     *     <li>any other status (e.g. 400 for a mapping error) → FAILED: retrying would fail the same way.</li>
     * </ul>
     */
    static Outcome toOutcome(PartialUpdateResponse response) {
        int status = response.getStatus();
        if (response.isSuccessful()) {
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
