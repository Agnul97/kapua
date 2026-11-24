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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.eclipse.kapua.commons.core.ServiceModule;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult.Outcome;
import org.eclipse.kapua.service.storable.model.id.StorableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the last published message (timestamp + id) of each info item of ONE type (channel, client or metric) in memory and writes it to the item
 * through a {@link LastPublishedStore} (the info repository of that type), at most once per item every N ({@code minAdvanceMillis}).
 * <p>
 * The stored value is a LOWER BOUND of the real last message time: only timestamps of messages already stored are recorded, values only move forward,
 * and items are never created. Losing an update (crash, eviction, race) only makes the stored value older, which costs readers and the TTL cleanup job
 * an extra message search, never a wrong result.
 * <p>
 * One instance per item type, each with its own settings and flush scheduler (see {@code DatastoreModule}), so the types can use different N
 * values and flush at different moments instead of loading the store all together. It never reads or writes the facades' info caches.
 *
 * @since 2.1.0
 */
public class LastPublishedTracker implements ServiceModule {

    private static final Logger LOG = LoggerFactory.getLogger(LastPublishedTracker.class);

    /**
     * Name (and Guice {@code @Named} value) of the channel tracker.
     */
    public static final String CHANNEL = "channel";

    /**
     * How long {@link #stop()} waits for a flush already running before the final flush.
     */
    private static final long STOP_WAIT_SECONDS = 30;

    /**
     * (timestamp, message id) pair: always kept together so the id matches the timestamp.
     */
    static final class Last {
        final long onMillis;
        final String messageId;

        Last(long onMillis, String messageId) {
            this.onMillis = onMillis;
            this.messageId = messageId;
        }

        // Keeps the newest pair. Used by every merge, so out-of-order records (late device data,
        // concurrent threads, requeued items) can never move the value backwards.
        static Last max(Last a, Last b) {
            return a.onMillis >= b.onMillis ? a : b;
        }
    }

    private final String name;                                  // item type, for logs and the thread name
    private final LastPublishedStore store;
    private final boolean enabled;
    private final long minAdvanceMillis;                        // N
    private final long flushIntervalMillis;
    private final long initialDelayMillis;
    private final int bulkSize;
    // Last value THIS JVM wrote per key. It is only a filter: losing an entry (eviction, restart)
    // costs one extra write (a noop once the scripted update is in, see DatastoreRepositoryBase#doUpdateLastPublished). Never a correctness issue.
    private final Cache<LastPublishedKey, Long> lastFlushed;
    // Replaced (not cleared) at each flush: volatile so record() always sees the current map.
    private volatile ConcurrentHashMap<LastPublishedKey, Last> pending = new ConcurrentHashMap<>();

    // Guards start/stop only. Not `this`: flush() locks `this`, and stop() waits for a running flush.
    private final Object lifecycleLock = new Object();
    private ScheduledExecutorService scheduler;

    /**
     * @param name                item type (e.g. "channel"), used in logs and in the flush thread name.
     * @param store               where the updates are written: the info repository of this type.
     * @param enabled             when false, {@link #record} is a noop and no scheduler is started.
     * @param minAdvanceMillis    N: each item is written at most once per N (by message time).
     * @param flushIntervalMillis delay between the end of a flush and the start of the next one.
     * @param initialDelayMillis  extra delay before the first flush only. Different values per type spread the flushes of the trackers apart; the
     *                            offset is approximate, it drifts by the flush durations.
     * @param bulkSize            updates per store call.
     * @param lastFlushedMaxSize  keys remembered as already written (the rate filter).
     */
    public LastPublishedTracker(String name, LastPublishedStore store, boolean enabled, long minAdvanceMillis, long flushIntervalMillis, long initialDelayMillis,
                                int bulkSize, long lastFlushedMaxSize) {
        if (minAdvanceMillis < 0 || flushIntervalMillis <= 0 || initialDelayMillis < 0 || bulkSize <= 0 || lastFlushedMaxSize < 0) {
            throw new IllegalArgumentException(String.format("Invalid %s last published settings: min_advance=%sms flush_interval=%sms initial_delay=%sms bulk_size=%s last_flushed.max_size=%s",
                    name, minAdvanceMillis, flushIntervalMillis, initialDelayMillis, bulkSize, lastFlushedMaxSize));
        }
        this.name = Objects.requireNonNull(name, "name");
        this.store = store;
        this.enabled = enabled;
        this.minAdvanceMillis = minAdvanceMillis;
        this.flushIntervalMillis = flushIntervalMillis;
        this.initialDelayMillis = initialDelayMillis;
        this.bulkSize = bulkSize;
        // maximumSize only, no time based expiry: the filter compares message timestamps, so an old entry is still a valid filter value.
        this.lastFlushed = CacheBuilder.newBuilder().maximumSize(lastFlushedMaxSize).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Records that a message for {@code key} has been STORED. Call it only after the message store succeeded, on every message (cache hit or miss of
     * the info facades). O(1), no I/O.
     *
     * @param key       the item.
     * @param on        the message timestamp (the one the message is indexed by).
     * @param messageId the stored message id.
     */
    public void record(LastPublishedKey key, Date on, StorableId messageId) {
        if (!enabled || key == null || on == null || messageId == null) {
            return;
        }
        long onMillis = on.getTime();
        Long lastFlushedOn = lastFlushed.getIfPresent(key);
        // The rate bound: an item already written less than N ago (by message time) is not queued.
        // Compared with the message timestamp, not the wall clock, so late data never passes the filter.
        if (lastFlushedOn != null && onMillis < lastFlushedOn + minAdvanceMillis) {
            return;
        }
        // Several messages for the same item between two flushes collapse into one entry (the newest).
        // Race with flush(): this may land in the map flush() has just swapped out and be lost.
        // Safe: the stored value only stays older; the item's next message queues it again.
        pending.merge(key, new Last(onMillis, messageId.toString()), Last::max);
    }

    /**
     * Every flush interval on the scheduler thread, and once on {@link #stop()}. {@code synchronized} only to keep the shutdown flush from overlapping
     * a scheduled one: {@link #record} never takes this lock.
     */
    synchronized void flush() {
        if (pending.isEmpty()) {
            return;
        }
        // Swap first, then send: record() keeps writing into the new map while this flush talks to the store,
        // so the message path never waits on the store.
        ConcurrentHashMap<LastPublishedKey, Last> batch = pending;
        pending = new ConcurrentHashMap<>();
        List<Map.Entry<LastPublishedKey, Last>> entries = new ArrayList<>(batch.entrySet());
        int requeuedItemsCount = 0;
        for (int i = 0; i < entries.size(); i += bulkSize) {
            List<Map.Entry<LastPublishedKey, Last>> chunk = entries.subList(i, Math.min(i + bulkSize, entries.size()));
            LastPublishedResult result;
            try {
                result = store.updateLastPublished(toUpdates(chunk), minAdvanceMillis);
            } catch (Exception ex) {
                // Store unreachable or the whole bulk refused: stop here instead of sending every remaining
                // chunk to a failing store. Everything not yet sent goes back to the queue.
                List<Map.Entry<LastPublishedKey, Last>> rest = entries.subList(i, entries.size());
                rest.forEach(this::requeue);
                LOG.warn("Last published {} flush failed, {} items kept for the next flush (queue size now {})", name, rest.size(), pending.size(), ex);
                return;
            }
            requeuedItemsCount = processItemsToRequeue(chunk, result, requeuedItemsCount);
        }
        if (requeuedItemsCount > 0) {
            LOG.info("Last published {} flush: {} of {} items to retry at the next flush", name, requeuedItemsCount, entries.size());
        }
    }

    private int processItemsToRequeue(List<Map.Entry<LastPublishedKey, Last>> chunk, LastPublishedResult result, int retried) {
        for (Map.Entry<LastPublishedKey, Last> e : chunk) {
            Outcome outcome = result.get(e.getKey());
            if (outcome == Outcome.RETRY) {
                requeue(e);
                retried++;
            } else if (outcome == Outcome.MISSING) {
                // The doc was deleted (e.g. by the TTL cleanup): drop this value, and forget the key so that, once the doc is
                // recreated, the next message is written at the next flush instead of up to N later. While the doc is
                // missing and the item keeps publishing, this costs at most one 404 per item per flush.
                // TODO: evaluate it better to do "lastFlushed.put(e.getKey(), e.getValue().onMillis);", it depends on the period of recreation of the doc. / how much 404 errors I can tolerate before recreation
                lastFlushed.invalidate(e.getKey());
            } else {
                // DONE (updated or noop) or FAILED (permanent error): nothing more to send for this value.
                // FAILED is logged by the store and retried at most once per N, instead of on every flush.
                lastFlushed.put(e.getKey(), e.getValue().onMillis);
            }
        }
        LOG.debug("Last published {} chunk of {} sent: {}", name, chunk.size(), result.counts());
        return retried;
    }

    /**
     * Merge, never put: a newer value recorded for the same key during the flush must win.
     */
    private void requeue(Map.Entry<LastPublishedKey, Last> e) {
        pending.merge(e.getKey(), e.getValue(), Last::max);
    }

    private static List<LastPublishedUpdate> toUpdates(List<Map.Entry<LastPublishedKey, Last>> chunk) {
        List<LastPublishedUpdate> updates = new ArrayList<>(chunk.size());
        for (Map.Entry<LastPublishedKey, Last> e : chunk) {
            updates.add(new LastPublishedUpdate(e.getKey(), e.getValue().onMillis, e.getValue().messageId));
        }
        return updates;
    }

    /**
     * Never lets an exception out: a {@link ScheduledExecutorService} silently cancels a task that throws.
     */
    private void flushSafely() {
        try {
            flush();
        } catch (Throwable t) {
            LOG.error("Unexpected error in the last published {} flush", name, t);
        }
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            doStart();
        }
    }

    private void doStart() {
        if (!enabled || scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "last-published-flush-" + name);
            t.setDaemon(true);
            return t;
        });
        // fixed delay, not fixed rate: a slow flush delays the next one instead of piling flushes up
        scheduler.scheduleWithFixedDelay(this::flushSafely, initialDelayMillis + flushIntervalMillis, flushIntervalMillis, TimeUnit.MILLISECONDS);
        LOG.info("Last published {} tracker started: min_advance={}ms flush_interval={}ms initial_delay={}ms bulk_size={}",
                name, minAdvanceMillis, flushIntervalMillis, initialDelayMillis, bulkSize);
    }

    /**
     * Stops the scheduler and flushes once more, so a graceful stop loses nothing. Must run before the store client is closed.
     */
    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            doStop();
        }
    }

    private void doStop() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(STOP_WAIT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("Last published {} flush still running after {}s, stopping anyway", name, STOP_WAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        scheduler = null;
        flushSafely();
        LOG.info("Last published {} tracker stopped, {} items not flushed", name, pending.size());
    }

    // for tests
    int pendingSize() {
        return pending.size();
    }

    // for tests
    Long lastFlushedOf(LastPublishedKey key) {
        return lastFlushed.getIfPresent(key);
    }
}
