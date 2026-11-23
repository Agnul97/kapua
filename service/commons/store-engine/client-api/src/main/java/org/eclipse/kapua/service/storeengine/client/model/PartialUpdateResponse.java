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

/**
 * Outcome of one {@link PartialUpdateRequest} of a bulk, as reported by the engine.
 * <p>
 * The raw item status is kept so that callers decide themselves what is retryable.
 *
 * @since 2.1.0
 */
public class PartialUpdateResponse {

    private final String index;
    private final String id;
    private final int status;
    private final String result;
    private final String errorType;
    private final String errorReason;

    /**
     * @param index       The index of the document.
     * @param id          The document id.
     * @param status      The item HTTP status (e.g. 200, 404, 409, 429).
     * @param result      The engine result on success ({@code updated}, {@code noop}), {@code null} on failure.
     * @param errorType   The engine error type on failure (e.g. {@code document_missing_exception}), {@code null} on success.
     * @param errorReason The engine error reason on failure, {@code null} on success.
     * @since 2.1.0
     */
    public PartialUpdateResponse(String index, String id, int status, String result, String errorType, String errorReason) {
        this.index = index;
        this.id = id;
        this.status = status;
        this.result = result;
        this.errorType = errorType;
        this.errorReason = errorReason;
    }

    public String getIndex() {
        return index;
    }

    public String getId() {
        return id;
    }

    public int getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public boolean isSuccessful() {
        return 200 <= status && status <= 299;
    }

    @Override
    public String toString() {
        return "PartialUpdateResponse{" + index + "/" + id + ", status=" + status + ", result=" + result + ", errorType=" + errorType + '}';
    }
}
