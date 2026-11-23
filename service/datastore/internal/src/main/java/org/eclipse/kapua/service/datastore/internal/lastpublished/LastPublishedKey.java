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

import org.eclipse.kapua.model.id.KapuaId;

import java.util.Objects;

/**
 * Identifies one info item inside the {@link LastPublishedTracker} of its type: scope + the derived info doc id ({@code ChannelInfoField.getOrDeriveId}
 * and equivalents). The item type is not part of the key: each type has its own tracker.
 * <p>
 * The scope is kept apart from the id because the store needs it to find where the item lives (e.g. the per-account index).
 *
 * @since 2.1.0
 */
public final class LastPublishedKey {

    private final KapuaId scopeId;
    private final String id;
    private final int hash;

    public LastPublishedKey(KapuaId scopeId, String id) {
        this.scopeId = Objects.requireNonNull(scopeId, "scopeId");
        this.id = Objects.requireNonNull(id, "id");
        // keys live in two hash maps and are looked up on every message: compute the hash once
        this.hash = Objects.hash(scopeId, id);
    }

    public KapuaId getScopeId() {
        return scopeId;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LastPublishedKey)) {
            return false;
        }
        LastPublishedKey that = (LastPublishedKey) o;
        return hash == that.hash && id.equals(that.id) && scopeId.equals(that.scopeId);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return scopeId.toCompactId() + "/" + id;
    }
}
