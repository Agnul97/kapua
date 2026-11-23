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
package org.eclipse.kapua.service.datastore.internal;

import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.qa.markers.junit.JUnitTests;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedKey;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult.Outcome;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedUpdate;
import org.eclipse.kapua.service.storeengine.client.model.UpdateRequest;
import org.eclipse.kapua.service.storeengine.client.model.UpdateResponse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.math.BigInteger;

/**
 * The Elasticsearch side of {@code updateLastPublished} shared by the info repositories ({@link DatastoreRepositoryBase}).
 */
@Category(JUnitTests.class)
public class DatastoreRepositoryBaseLastPublishedTest {

    private static Outcome outcome(int status) {
        UpdateResponse response = new UpdateResponse("1", "i");
        response.setStatus(status);
        return DatastoreRepositoryBase.toLastPublishedOutcome(response);
    }

    @Test
    public void statusMapping() {
        Assert.assertEquals(Outcome.DONE, outcome(200));      // updated or noop
        Assert.assertEquals(Outcome.FAILED, outcome(0));      // no status reported
        Assert.assertEquals(Outcome.MISSING, outcome(404));   // document_missing_exception / index_not_found_exception
        Assert.assertEquals(Outcome.RETRY, outcome(409));     // version conflict
        Assert.assertEquals(Outcome.RETRY, outcome(429));     // push-back
        Assert.assertEquals(Outcome.RETRY, outcome(503));
        Assert.assertEquals(Outcome.FAILED, outcome(400));    // mapping error
    }

    @Test
    public void plainUpdateWritesBothFieldsTogether() throws Exception {
        LastPublishedKey key = new LastPublishedKey(new KapuaEid(BigInteger.ONE), "abc");
        UpdateRequest request = DatastoreRepositoryBase.toLastPublishedRequest("1-data-channel", new LastPublishedUpdate(key, 0L, "m1"));

        Assert.assertEquals("1-data-channel", request.getIndex());
        Assert.assertEquals("abc", request.getId());
        Assert.assertFalse(request.isDocAsUpsert());
        Assert.assertNull(request.getStorable());
        Assert.assertEquals(2, request.getPartialDoc().size());
        Assert.assertEquals("1970-01-01T00:00:00.000Z", request.getPartialDoc().get("last_message_on"));
        Assert.assertEquals("m1", request.getPartialDoc().get("last_message_id"));
    }
}
