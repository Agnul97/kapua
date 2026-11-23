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
package org.eclipse.kapua.service.storeengine.client.model;

import java.util.Collections;
import java.util.Map;

/**
 * A partial update of one EXISTING document, sent in a bulk by
 * {@link org.eclipse.kapua.service.storeengine.client.StoreEngineClientWrapper#partialUpdate(java.util.List)}: the given fields are merged into the
 * stored document (no condition, last write wins).
 * <p>
 * There is deliberately no upsert option: a missing document is reported as not found and never created. There is no {@code retry_on_conflict}
 * either: a version conflict is reported per item (409) and the caller decides whether to retry.
 *
 * @since 2.1.0
 */
public class PartialUpdateRequest {

    private final String index;
    private final String id;
    private final Map<String, Object> doc;

    /**
     * @param index           The index of the document.
     * @param id              The document id.
     * @param doc   The fields to set.
     * @since 2.1.0
     */
    public PartialUpdateRequest(String index, String id, Map<String, Object> doc) {
        this.index = index;
        this.id = id;
        this.doc = doc != null ? doc : Collections.emptyMap();
    }

    public String getIndex() {
        return index;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getDoc() {
        return doc;
    }
}
