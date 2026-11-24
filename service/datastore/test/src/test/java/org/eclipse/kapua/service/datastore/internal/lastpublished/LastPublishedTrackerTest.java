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
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.qa.markers.junit.JUnitTests;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult.Outcome;
import org.eclipse.kapua.service.storable.model.id.StorableIdImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Category(JUnitTests.class)
public class LastPublishedTrackerTest {

    private static final long N = TimeUnit.HOURS.toMillis(3);
    private static final KapuaEid SCOPE = new KapuaEid(BigInteger.ONE);
    private static final LastPublishedKey A = new LastPublishedKey(SCOPE, "a");
    private static final LastPublishedKey B = new LastPublishedKey(SCOPE, "b");
    private static final LastPublishedKey C = new LastPublishedKey(SCOPE, "c");

    /**
     * In-memory store: records every call, answers with a configurable outcome per key, or throws on configured calls.
     */
    private static class FakeStore implements LastPublishedStore {
        final List<List<LastPublishedUpdate>> calls = new ArrayList<>();
        final Map<LastPublishedKey, LastPublishedUpdate> written = new HashMap<>();
        Function<LastPublishedKey, Outcome> outcome = k -> Outcome.DONE;
        int failFromCall = Integer.MAX_VALUE;

        @Override
        public LastPublishedResult updateLastPublished(List<LastPublishedUpdate> updates, long minAdvanceMillis) throws KapuaException {
            calls.add(new ArrayList<>(updates));
            if (calls.size() > failFromCall) {
                throw KapuaException.internalError("store down");
            }
            LastPublishedResult result = new LastPublishedResult();
            for (LastPublishedUpdate u : updates) {
                Outcome o = outcome.apply(u.getKey());
                if (o == Outcome.DONE) {
                    written.put(u.getKey(), u);
                }
                result.put(u.getKey(), o);
            }
            return result;
        }

        int sentCount() {
            return calls.stream().mapToInt(List::size).sum();
        }
    }

    private static LastPublishedTracker tracker(FakeStore store, int bulkSize) {
        return new LastPublishedTracker("test", store, true, N, 60_000, 0, bulkSize, 1000);
    }

    private static void record(LastPublishedTracker t, LastPublishedKey key, long on, String id) {
        t.record(key, new Date(on), new StorableIdImpl(id));
    }

    @Test
    public void severalMessagesBetweenFlushesCollapseIntoTheNewest() {
        FakeStore store = new FakeStore();
        LastPublishedTracker t = tracker(store, 1000);

        record(t, A, 2_000, "m2");
        record(t, A, 3_000, "m3");
        record(t, A, 1_000, "m1"); // late device data: must not win
        t.flush();

        Assert.assertEquals(1, store.sentCount());
        Assert.assertEquals(3_000, store.written.get(A).getOnMillis());
        Assert.assertEquals("m3", store.written.get(A).getMessageId());
    }

    @Test
    public void filterSkipsItemsWrittenLessThanNAgo() {
        FakeStore store = new FakeStore();
        LastPublishedTracker t = tracker(store, 1000);

        record(t, A, 0, "m0");
        t.flush();
        record(t, A, N - 1, "m1"); // less than N after the written value: filtered
        Assert.assertEquals(0, t.pendingSize());
        record(t, A, N, "m2");     // exactly N after: queued
        Assert.assertEquals(1, t.pendingSize());
        t.flush();

        Assert.assertEquals(2, store.sentCount());
        Assert.assertEquals("m2", store.written.get(A).getMessageId());
    }

    @Test
    public void emptyQueueSendsNothing() {
        FakeStore store = new FakeStore();
        tracker(store, 1000).flush();
        Assert.assertTrue(store.calls.isEmpty());
    }

    @Test
    public void disabledTrackerRecordsNothing() {
        FakeStore store = new FakeStore();
        LastPublishedTracker t = new LastPublishedTracker("test", store, false, N, 60_000, 0, 1000, 1000);
        record(t, A, 1_000, "m1");
        t.flush();
        Assert.assertEquals(0, t.pendingSize());
        Assert.assertTrue(store.calls.isEmpty());
    }

    @Test
    public void flushIsChunkedByBulkSize() {
        FakeStore store = new FakeStore();
        LastPublishedTracker t = tracker(store, 2);
        record(t, A, 1, "a");
        record(t, B, 1, "b");
        record(t, C, 1, "c");
        t.flush();

        Assert.assertEquals(2, store.calls.size());
        Assert.assertEquals(3, store.sentCount());
    }

    @Test
    public void retryIsRequeuedAndDoesNotOverwriteANewerValue() {
        FakeStore store = new FakeStore();
        store.outcome = k -> Outcome.RETRY;
        LastPublishedTracker t = tracker(store, 1000);

        record(t, A, 1_000, "old");
        t.flush();
        Assert.assertEquals(1, t.pendingSize());
        Assert.assertNull("a RETRY is not marked as flushed", t.lastFlushedOf(A));

        record(t, A, 5_000, "new"); // newer value recorded after the failed flush
        store.outcome = k -> Outcome.DONE;
        t.flush();
        Assert.assertEquals("new", store.written.get(A).getMessageId());

        // and the other way round: a requeued newer value is not replaced by an older one recorded later
        store.outcome = k -> Outcome.RETRY;
        record(t, B, 9_000, "newer");
        t.flush();
        record(t, B, 8_000, "older");
        store.outcome = k -> Outcome.DONE;
        t.flush();
        Assert.assertEquals("newer", store.written.get(B).getMessageId());
    }

    @Test
    public void failedIsMarkedFlushed() {
        FakeStore store = new FakeStore();
        store.outcome = k -> Outcome.FAILED;
        LastPublishedTracker t = tracker(store, 1000);

        record(t, B, 1_000, "b");
        t.flush();

        Assert.assertEquals(0, t.pendingSize());
        Assert.assertEquals(Long.valueOf(1_000), t.lastFlushedOf(B));
        // the next messages within N are filtered: a permanent error is retried at most once per N
        record(t, B, 2_000, "b2");
        Assert.assertEquals(0, t.pendingSize());
    }

    @Test
    public void missingIsDroppedAndForgottenSoARecreatedDocIsWrittenAtTheNextFlush() {
        FakeStore store = new FakeStore();
        LastPublishedTracker t = tracker(store, 1000);

        // written once: the key is marked as flushed
        record(t, A, 0, "a0");
        t.flush();
        Assert.assertEquals(Long.valueOf(0), t.lastFlushedOf(A));

        // the doc is deleted: the value N later gets a 404, it is not requeued and the key is forgotten
        store.outcome = k -> Outcome.MISSING;
        record(t, A, N, "a1");
        t.flush();
        Assert.assertEquals(0, t.pendingSize());
        Assert.assertNull("a MISSING key is forgotten", t.lastFlushedOf(A));

        // the doc is recreated: a message less than N after the 404 value is still queued and written at the next flush
        store.outcome = k -> Outcome.DONE;
        record(t, A, N + 1_000, "a2");
        Assert.assertEquals(1, t.pendingSize());
        t.flush();
        Assert.assertEquals("a2", store.written.get(A).getMessageId());
        Assert.assertEquals(Long.valueOf(N + 1_000), t.lastFlushedOf(A));
    }

    @Test
    public void flushStopsAtTheFirstFailureAndRequeuesTheRest() {
        FakeStore store = new FakeStore();
        store.failFromCall = 1; // first chunk ok, second throws
        LastPublishedTracker t = tracker(store, 1);

        record(t, A, 1, "a");
        record(t, B, 1, "b");
        record(t, C, 1, "c");
        t.flush();

        Assert.assertEquals("third chunk never sent", 2, store.calls.size());
        Assert.assertEquals("failed chunk and unsent chunk requeued", 2, t.pendingSize());

        store.failFromCall = Integer.MAX_VALUE;
        t.flush();
        Assert.assertEquals(3, store.written.size());
        Assert.assertEquals(0, t.pendingSize());
    }

    @Test
    public void concurrentRecordsKeepTheMax() throws Exception {
        FakeStore store = new FakeStore();
        LastPublishedTracker t = tracker(store, 1000);
        int threads = 8;
        int perThread = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int th = 0; th < threads; th++) {
            int offset = th;
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    long on = (long) i * threads + offset;
                    record(t, A, on, "m" + on);
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        Assert.assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        t.flush();

        long max = (long) (perThread - 1) * threads + (threads - 1);
        Assert.assertEquals(max, store.written.get(A).getOnMillis());
        Assert.assertEquals("m" + max, store.written.get(A).getMessageId());
    }

    @Test
    public void stopFlushesWhatIsPending() {
        FakeStore store = new FakeStore();
        LastPublishedTracker t = tracker(store, 1000);
        t.start();
        record(t, A, 1_000, "a");
        t.stop();

        Assert.assertEquals("a", store.written.get(A).getMessageId());
    }

    @Test
    public void unreportedUpdateIsRetried() {
        LastPublishedResult result = new LastPublishedResult();
        Assert.assertTrue(result.isRetry(A));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeInitialDelayIsRejected() {
        new LastPublishedTracker("test", new FakeStore(), true, N, 60_000, -1, 1000, 1000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSettingsAreRejected() {
        new LastPublishedTracker("test", new FakeStore(), true, N, 0, 0, 1000, 1000);
    }
}
