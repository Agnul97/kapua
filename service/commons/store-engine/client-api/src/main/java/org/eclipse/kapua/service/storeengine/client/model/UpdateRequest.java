/*******************************************************************************
 * Copyright (c) 2017, 2022 Eurotech and/or its affiliates and others
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

import org.eclipse.kapua.service.storable.model.Storable;

import java.util.Map;
import java.util.Objects;

/**
 * Update {@link Request} definition.
 *
 * @since 1.0.0
 */
public class UpdateRequest extends Request {

    /**
     * When set, sent as the document instead of the marshalled {@link #getStorable()}: only these fields are merged into the stored document.
     *
     * @since 2.1.0
     */
    private Map<String, Object> partialDoc;

    /**
     * Whether a missing document is created ({@code doc_as_upsert}). {@code true} for every request built with the public constructor.
     *
     * @since 2.1.0
     */
    private boolean docAsUpsert = true;

    /**
     * Constructor.
     *
     * @param id             The Object identifier.
     * @param index          The index.
     * @param storable       The Object to update.
     * @since 1.0.0
     */
    public UpdateRequest(String id, String index, Storable storable) {
        super(id, index, storable);
    }

    /**
     * A partial update of an EXISTING document: only the given fields are merged into it, and a missing document is NOT created (its bulk item
     * answers 404). Supported by the bulk upsert only.
     *
     * @param id         The document identifier.
     * @param index      The index.
     * @param partialDoc The fields to set, already in their stored format.
     * @since 2.1.0
     */
    public static UpdateRequest partial(String id, String index, Map<String, Object> partialDoc) {
        UpdateRequest request = new UpdateRequest(id, index, null);
        request.partialDoc = Objects.requireNonNull(partialDoc, "partialDoc");
        request.docAsUpsert = false;
        return request;
    }

    /**
     * @return the fields of a {@link #partial} request, {@code null} for a full document update.
     * @since 2.1.0
     */
    public Map<String, Object> getPartialDoc() {
        return partialDoc;
    }

    /**
     * @return whether a missing document is created.
     * @since 2.1.0
     */
    public boolean isDocAsUpsert() {
        return docAsUpsert;
    }
}
