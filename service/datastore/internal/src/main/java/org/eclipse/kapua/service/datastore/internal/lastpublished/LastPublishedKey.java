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
 * Identifies one info item: scope + {@link InfoType} + the derived info doc id ({@code ChannelInfoField.getOrDeriveId} and equivalents).
 * <p>
 * It is never a facade cache key (the clients cache, for instance, keys on the raw clientId): the tracker must not share keys with those caches.
 *
 * @since 2.1.0
 */
public final class LastPublishedKey {

    private final KapuaId scopeId;
    private final InfoType type;
    private final String id;
    private final int hash;

    public LastPublishedKey(KapuaId scopeId, InfoType type, String id) {
        this.scopeId = Objects.requireNonNull(scopeId, "scopeId");
        this.type = Objects.requireNonNull(type, "type");
        this.id = Objects.requireNonNull(id, "id");
        // keys live in two hash maps and are looked up on every message: compute the hash once
        this.hash = Objects.hash(scopeId, type, id);
    }

    public static LastPublishedKey channel(KapuaId scopeId, String channelInfoId) {
        return new LastPublishedKey(scopeId, InfoType.CHANNEL, channelInfoId);
    }

    public static LastPublishedKey client(KapuaId scopeId, String clientInfoId) {
        return new LastPublishedKey(scopeId, InfoType.CLIENT, clientInfoId);
    }

    public static LastPublishedKey metric(KapuaId scopeId, String metricInfoId) {
        return new LastPublishedKey(scopeId, InfoType.METRIC, metricInfoId);
    }

    public KapuaId getScopeId() {
        return scopeId;
    }

    public InfoType getType() {
        return type;
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
        return hash == that.hash && type == that.type && id.equals(that.id) && scopeId.equals(that.scopeId);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return type + "[" + scopeId.toCompactId() + "/" + id + "]";
    }
}
