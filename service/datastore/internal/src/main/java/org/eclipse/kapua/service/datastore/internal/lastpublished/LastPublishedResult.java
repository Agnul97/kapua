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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-update outcome of {@link LastPublishedStore#updateLastPublished(java.util.List, long)}.
 *
 * @since 2.1.0
 */
public final class LastPublishedResult {

    public enum Outcome {
        /**
         * Written, or a noop because the stored value is already at least as new (minus the minimum advance).
         */
        DONE,
        /**
         * The item does not exist (e.g. deleted by the TTL cleanup job). It is never created by the store.
         */
        MISSING,
        /**
         * Transient failure (conflict after retries, push-back, server error): try again at the next flush.
         */
        RETRY,
        /**
         * Permanent failure for this update (e.g. a mapping error): retrying would fail the same way.
         */
        FAILED
    }

    private final Map<LastPublishedKey, Outcome> outcomes = new HashMap<>();

    public void put(LastPublishedKey key, Outcome outcome) {
        outcomes.put(key, outcome);
    }

    public Outcome get(LastPublishedKey key) {
        // An update the store did not report on was not confirmed: treat it as retryable, so it is never silently dropped.
        return outcomes.getOrDefault(key, Outcome.RETRY);
    }

    public boolean isRetry(LastPublishedKey key) {
        return get(key) == Outcome.RETRY;
    }

    /**
     * @return how many updates ended with each {@link Outcome}, for logging.
     */
    public Map<Outcome, Integer> counts() {
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        for (Outcome o : outcomes.values()) {
            counts.merge(o, 1, Integer::sum);
        }
        return counts;
    }
}
