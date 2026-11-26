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
package org.eclipse.kapua.service.datastore.internal.model;

import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.storable.model.id.StorableId;
import org.eclipse.kapua.service.storable.model.id.StorableIdImpl;

/**
 * Conversions used by the info entities ({@link ChannelInfoImpl}, {@link ClientInfoImpl}, {@link MetricInfoImpl}) to keep their JPA columns as plain
 * types (strings, {@link KapuaEid}) while their public API keeps exposing {@link StorableId} and {@link KapuaId}.
 *
 * @since 2.1.0
 */
final class InfoEntityUtils {

    private InfoEntityUtils() {
    }

    static String toColumn(StorableId storableId) {
        return storableId != null ? storableId.toString() : null;
    }

    static StorableId toStorableId(String column) {
        return column != null ? new StorableIdImpl(column) : null;
    }

    static KapuaEid toKapuaEid(KapuaId kapuaId) {
        return kapuaId != null ? KapuaEid.parseKapuaId(kapuaId) : null;
    }
}
