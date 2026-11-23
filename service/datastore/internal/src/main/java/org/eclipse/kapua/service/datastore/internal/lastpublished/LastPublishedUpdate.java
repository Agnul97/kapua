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

/**
 * One (lastMessageOn, lastMessageId) pair to write on an item. The two values always travel together, so the id always matches the timestamp.
 *
 * @since 2.1.0
 */
public final class LastPublishedUpdate {

    private final LastPublishedKey key;
    private final long onMillis;
    private final String messageId;

    public LastPublishedUpdate(LastPublishedKey key, long onMillis, String messageId) {
        this.key = key;
        this.onMillis = onMillis;
        this.messageId = messageId;
    }

    public LastPublishedKey getKey() {
        return key;
    }

    public long getOnMillis() {
        return onMillis;
    }

    public String getMessageId() {
        return messageId;
    }

    @Override
    public String toString() {
        return key + "@" + onMillis + "/" + messageId;
    }
}
