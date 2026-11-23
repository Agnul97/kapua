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

import java.util.List;

/**
 * Where {@link LastPublishedTracker} writes the last published message of each item. The tracker depends only on this interface, so the storage can
 * change (Elasticsearch info docs now, SQL tables possibly later) without touching the tracker.
 *
 * @since 2.1.0
 */
public interface LastPublishedStore {

    /**
     * Moves (lastMessageOn, lastMessageId) forward on EXISTING items only.
     * <p>
     * Contract every implementation must honour:
     * <ul>
     *     <li>never creates an item (a missing one is reported as {@link LastPublishedResult.Outcome#MISSING});</li>
     *     <li>never moves a value backwards;</li>
     *     <li>ignores advances smaller than {@code minAdvanceMillis} ({@link LastPublishedResult.Outcome#DONE}, nothing written);</li>
     *     <li>writes the timestamp and the message id together, or neither.</li>
     * </ul>
     * TODO: the current {@link EsLastPublishedStore} (plain update, for the stress test) does NOT honour the "never backwards" and
     *  "minimum advance" points yet: it must switch to its scripted update for correctness.
     *
     * @param updates          the updates, at most one per key.
     * @param minAdvanceMillis the smallest advance worth writing.
     * @return the outcome per update.
     * @throws KapuaException when the whole call failed: nothing can be assumed written, the caller retries every update.
     */
    LastPublishedResult record(List<LastPublishedUpdate> updates, long minAdvanceMillis) throws KapuaException;
}
