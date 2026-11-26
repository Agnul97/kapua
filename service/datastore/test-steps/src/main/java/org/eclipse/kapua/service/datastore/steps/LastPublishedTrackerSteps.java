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
package org.eclipse.kapua.service.datastore.steps;

import com.google.inject.Singleton;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.message.device.data.KapuaDataChannel;
import org.eclipse.kapua.message.device.data.KapuaDataMessage;
import org.eclipse.kapua.message.device.data.KapuaDataMessageFactory;
import org.eclipse.kapua.message.device.data.KapuaDataPayload;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.qa.common.StepData;
import org.eclipse.kapua.qa.common.TestBase;
import org.eclipse.kapua.service.account.Account;
import org.eclipse.kapua.service.datastore.internal.ChannelInfoRegistryFacade;
import org.eclipse.kapua.service.datastore.internal.ChannelInfoRepository;
import org.eclipse.kapua.service.datastore.internal.ClientInfoRegistryFacade;
import org.eclipse.kapua.service.datastore.internal.ClientInfoRepository;
import org.eclipse.kapua.service.datastore.internal.ConfigurationProvider;
import org.eclipse.kapua.service.datastore.internal.MessageRepository;
import org.eclipse.kapua.service.datastore.internal.MessageStoreFacadeImpl;
import org.eclipse.kapua.service.datastore.internal.MetricInfoRegistryFacade;
import org.eclipse.kapua.service.datastore.internal.MetricInfoRepository;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult.Outcome;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedStore;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedTracker;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedUpdate;
import org.eclipse.kapua.service.datastore.internal.mediator.ChannelInfoField;
import org.eclipse.kapua.service.datastore.internal.mediator.DatastoreUtils;
import org.eclipse.kapua.service.datastore.internal.model.query.ChannelInfoQueryImpl;
import org.eclipse.kapua.service.datastore.model.ChannelInfo;
import org.eclipse.kapua.service.datastore.model.ChannelInfoListResult;
import org.eclipse.kapua.service.datastore.model.query.predicate.DatastorePredicateFactory;
import org.eclipse.kapua.service.device.registry.Device;
import org.eclipse.kapua.service.storable.model.id.StorableId;
import org.eclipse.kapua.service.storable.model.id.StorableIdFactory;
import org.eclipse.kapua.service.storable.model.query.predicate.AndPredicate;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Steps for the channel {@link LastPublishedTracker}: they store messages through a {@link MessageStoreFacadeImpl} wired to a tracker owned by the
 * scenario, then read {@code lastMessageOn} / {@code lastMessageId} of the channel info through the {@link ChannelInfoRepository}.
 * <p>
 * The scenario builds its own tracker (enabled, with its own settings) instead of using the injected one: the injected tracker reads its settings once,
 * when the locator is first used in the JVM, so it cannot be enabled reliably from a feature. Everything else is the real code: the facade hook,
 * the channel info repository and the bulk write.
 * <p>
 * Only repositories are used (no store engine client), so the steps work with any repository binding (Elasticsearch now, SQL later). The repository is
 * read directly, not through {@code ChannelInfoRegistryService}: the service replaces the stored values with a message search, or clears them.
 * The tracker writes are counted by a {@link LastPublishedStore} wrapping the repository, instead of reading a store specific document version.
 * <p>
 * {@link Singleton} like the other step classes: without a scope cucumber-guice builds a new instance for every step, and the tracker created by one
 * step would be gone in the next. The state is reset after each scenario.
 */
@Singleton
public class LastPublishedTrackerSteps extends TestBase {

    private static final Logger LOG = LoggerFactory.getLogger(LastPublishedTrackerSteps.class);

    private static final String LAST_ACCOUNT = "LastAccount";
    private static final String DEVICE = "Device";
    /**
     * Allowance for the time a flush, a store call and a refresh take, on top of the theoretical bounds.
     */
    private static final long SLACK_MILLIS = 2_000;

    private LastPublishedTracker tracker;
    private long minAdvanceMillis;
    private long flushIntervalMillis;
    private MessageStoreFacadeImpl trackedFacade;
    // the datastore id each stored message got, to compare with last_message_id
    private final Map<KapuaDataMessage, StorableId> storedIds = new IdentityHashMap<>();
    // per client id, the newest message stored through the tracked facade
    private final Map<String, KapuaDataMessage> newestByClient = new HashMap<>();
    // per client id, the datastore id of every stored message by its timestamp: a stored (on, id) pair must be one of these
    private final Map<String, TreeMap<Long, String>> timelineByClient = new HashMap<>();
    // per channel info id, the updates the tracker wrote (DONE): written by the flush thread, read by the steps
    private final Map<String, Integer> writesByChannelInfoId = new ConcurrentHashMap<>();

    @Inject
    public LastPublishedTrackerSteps(StepData stepData) {
        super(stepData);
    }

    @After
    public void stopTracker() {
        if (tracker != null) {
            tracker.stop();
            tracker = null;
        }
        trackedFacade = null;
        storedIds.clear();
        newestByClient.clear();
        timelineByClient.clear();
        writesByChannelInfoId.clear();
    }

    @Given("A channel last published tracker with min advance {int} seconds and flush interval {int} milliseconds")
    public void createChannelTracker(int minAdvanceSeconds, int flushIntervalMillis) {
        KapuaLocator locator = KapuaLocator.getInstance();
        this.minAdvanceMillis = TimeUnit.SECONDS.toMillis(minAdvanceSeconds);
        this.flushIntervalMillis = flushIntervalMillis;
        ChannelInfoRepository channelInfoRepository = locator.getComponent(ChannelInfoRepository.class);
        // the real repository write, counting the updates it reports as written
        LastPublishedStore countingStore = (updates, minAdvance) -> {
            LastPublishedResult result = channelInfoRepository.updateLastPublished(updates, minAdvance);
            for (LastPublishedUpdate update : updates) {
                if (result.get(update.getKey()) == Outcome.DONE) {
                    writesByChannelInfoId.merge(update.getKey().getId(), 1, Integer::sum);
                }
            }
            return result;
        };
        tracker = new LastPublishedTracker("channel-it", countingStore, true,
                this.minAdvanceMillis, flushIntervalMillis, 0, 1000, 10_000);
        trackedFacade = new MessageStoreFacadeImpl(
                locator.getComponent(ConfigurationProvider.class),
                locator.getFactory(StorableIdFactory.class),
                locator.getComponent(ClientInfoRegistryFacade.class),
                locator.getComponent(ChannelInfoRegistryFacade.class),
                locator.getComponent(MetricInfoRegistryFacade.class),
                locator.getComponent(MessageRepository.class),
                locator.getComponent(MetricInfoRepository.class),
                locator.getComponent(ChannelInfoRepository.class),
                locator.getComponent(ClientInfoRepository.class),
                locator.getComponent(DatastoreUtils.class),
                tracker);
    }

    @When("I store the messages from list {string} through the channel last published tracker")
    public void storeThroughTrackedFacade(String listKey) throws Exception {
        List<KapuaDataMessage> messages = (List<KapuaDataMessage>) stepData.get(listKey);
        for (KapuaDataMessage message : messages) {
            storeTracked(message);
        }
    }

    /**
     * Runs the tracker over time: stores a message stamped with the current time every {@code everyMillis}, and after each one reads the channel doc
     * and checks what must hold at any moment while the scheduler flushes in the background:
     * <ul>
     *     <li>the stored (last_message_on, last_message_id) pair is one of the stored messages (never invented, never mismatched);</li>
     *     <li>the first value shows up within two flush intervals of the first message;</li>
     *     <li>afterwards the value never lags the newest stored message by more than N + two flush intervals (a value is written at most once per N,
     *     at the first flush after a message N newer than the written one arrives).</li>
     * </ul>
     * The tracker must be started.
     */
    @When("I store a message with the current time for client {string} on topic {string} through the channel last published tracker every {int} milliseconds for {int} seconds, checking the channel each time")
    public void storeOverTime(String clientId, String topic, int everyMillis, int seconds) throws Exception {
        long end = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);
        long firstOn = -1;
        int samples = 0;
        while (System.currentTimeMillis() < end) {
            KapuaDataMessage message = newMessage(clientId, topic, new Date());
            storeTracked(message);
            if (firstOn < 0) {
                firstOn = message.getCapturedOn().getTime();
            }
            checkWhileRunning(clientId, firstOn);
            samples++;
            Thread.sleep(everyMillis);
        }
        LOG.info("Stored and checked {} messages for client {}", samples, clientId);
    }

    /**
     * After the messages stop: once the pending value is flushed, the stored value is the newest message that passed the N filter, so it is less
     * than N behind the newest stored message.
     */
    @Then("The last published message of client {string} is less than the min advance behind the newest stored message within {int} seconds")
    public void lastPublishedWithinMinAdvance(String clientId, int seconds) throws Exception {
        long newestOn = newest(clientId).getCapturedOn().getTime();
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);
        long lag;
        do {
            ChannelInfo channelInfo = channelInfo(clientId);
            Assert.assertNotNull("No channel info for client " + clientId, channelInfo);
            lag = Long.MAX_VALUE;
            if (channelInfo.getLastMessageOn() != null) {
                long on = assertConsistentPair(clientId, channelInfo);
                lag = newestOn - on;
                if (lag < minAdvanceMillis) {
                    return;
                }
            }
            Thread.sleep(500);
        } while (System.currentTimeMillis() < deadline);
        Assert.fail("The last published message of client " + clientId + " is " + lag + "ms behind the newest stored message, the min advance is " + minAdvanceMillis + "ms");
    }

    /**
     * The tracker writes on the channel, as reported written by the repository. This checks the rate bound: about one write per N, not one per message.
     */
    @Then("The channel info doc of client {string} was written by the tracker between {int} and {int} times")
    public void trackerWrites(String clientId, int min, int max) throws Exception {
        ChannelInfo channelInfo = channelInfo(clientId);
        Assert.assertNotNull("No channel info for client " + clientId, channelInfo);
        long writes = trackerWritesOf(channelInfo);
        LOG.info("Channel of client {}: {} tracker writes for {} stored messages", clientId, writes, timelineByClient.get(clientId).size());
        Assert.assertTrue("The tracker wrote " + writes + " times, expected between " + min + " and " + max, writes >= min && writes <= max);
    }

    /**
     * One flush, now: {@code start} schedules the first flush one interval later, {@code stop} cancels it and flushes once (the shutdown flush).
     */
    @When("I flush the channel last published tracker")
    public void flushTracker() {
        tracker.start();
        tracker.stop();
    }

    @When("I start the channel last published tracker")
    public void startTracker() {
        tracker.start();
    }

    @Then("The channel of client {string} has the newest stored message as last published message")
    public void lastPublishedIsNewest(String clientId) throws Exception {
        assertLastPublished(clientId, null, newest(clientId));
    }

    @Then("The channel of client {string} has the newest stored message as last published message within {int} seconds")
    public void lastPublishedIsNewestWithin(String clientId, int seconds) throws Exception {
        KapuaDataMessage expected = newest(clientId);
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);
        while (true) {
            ChannelInfo channelInfo = channelInfo(clientId);
            if (channelInfo != null && matches(channelInfo, expected)) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                assertLastPublished(clientId, null, expected); // fails with the details
                return;
            }
            Thread.sleep(500);
        }
    }

    @Then("The channel of client {string} has the newest message of list {string} as last published message")
    public void lastPublishedIsNewestOfList(String clientId, String listKey) throws Exception {
        List<KapuaDataMessage> messages = (List<KapuaDataMessage>) stepData.get(listKey);
        KapuaDataMessage expected = messages.stream()
                .filter(m -> clientId.equals(m.getClientId()))
                .reduce((a, b) -> b.getCapturedOn().after(a.getCapturedOn()) ? b : a)
                .orElseThrow(() -> new AssertionError("No message of client " + clientId + " in list " + listKey));
        assertLastPublished(clientId, null, expected);
    }

    @Then("The channel of client {string} has no last published message")
    public void noLastPublished(String clientId) throws Exception {
        ChannelInfo channelInfo = channelInfo(clientId);
        Assert.assertNotNull("No channel info for client " + clientId, channelInfo);
        Assert.assertNull("Unexpected lastMessageOn", channelInfo.getLastMessageOn());
        Assert.assertNull("Unexpected lastMessageId", channelInfo.getLastMessageId());
    }

    @Then("The channel info doc of client {string} does not exist")
    public void noChannelDoc(String clientId) throws Exception {
        Assert.assertNull("The channel info doc was (re)created", channelInfo(clientId));
    }

    /**
     * Deletes the doc in the store engine only: the facade's channel cache still says the channel exists, as after the TTL cleanup job, so the next
     * messages do not recreate it.
     */
    @When("I delete the channel info doc of client {string} from the store engine")
    public void deleteChannelDoc(String clientId) throws Exception {
        ChannelInfoQueryImpl query = channelQuery(clientId, null);
        // a delete by query only sees searchable docs: refresh first, or a doc indexed a moment ago is silently skipped
        refresh();
        Assert.assertEquals("Channel info docs of client " + clientId + " before the delete", 1, channelInfoRepository().count(query));
        channelInfoRepository().delete(query);
        refresh();
        Assert.assertEquals("The channel info doc of client " + clientId + " was not deleted", 0, channelInfoRepository().count(query));
    }

    // ---- several channels of one client: the doc is looked up by client id AND channel

    @Then("The channel {string} of client {string} has the newest message of list {string} as last published message")
    public void channelLastPublishedIsNewestOfList(String channel, String clientId, String listKey) throws Exception {
        List<KapuaDataMessage> messages = (List<KapuaDataMessage>) stepData.get(listKey);
        KapuaDataMessage expected = messages.stream()
                .filter(m -> clientId.equals(m.getClientId()) && channel.equals(channelOf(m)))
                .reduce((a, b) -> b.getCapturedOn().after(a.getCapturedOn()) ? b : a)
                .orElseThrow(() -> new AssertionError("No message of client " + clientId + " on channel " + channel + " in list " + listKey));
        assertLastPublished(clientId, channel, expected);
    }

    @Then("The channel info doc of channel {string} of client {string} does not exist")
    public void noChannelDoc(String channel, String clientId) throws Exception {
        Assert.assertNull("Unexpected channel info doc for channel " + channel, channelInfo(clientId, channel));
    }

    /**
     * Exact count of tracker writes on one channel: the rate bound decides it.
     */
    @Then("The channel {string} of client {string} was written by the tracker {int} time(s)")
    public void channelTrackerWrites(String channel, String clientId, int expected) throws Exception {
        ChannelInfo channelInfo = channelInfo(clientId, channel);
        Assert.assertNotNull("No channel info for channel " + channel + " of client " + clientId, channelInfo);
        Assert.assertEquals("Tracker writes on channel " + channel, expected, trackerWritesOf(channelInfo));
    }

    private void storeTracked(KapuaDataMessage message) throws Exception {
        StorableId id = trackedFacade.store(message, UUID.randomUUID().toString());
        storedIds.put(message, id);
        newestByClient.merge(message.getClientId(), message, (a, b) -> b.getCapturedOn().after(a.getCapturedOn()) ? b : a);
        timelineByClient.computeIfAbsent(message.getClientId(), c -> new TreeMap<>()).put(message.getCapturedOn().getTime(), id.toString());
    }

    private void checkWhileRunning(String clientId, long firstOn) throws Exception {
        long newestOn = newest(clientId).getCapturedOn().getTime();
        long running = newestOn - firstOn;
        ChannelInfo channelInfo = channelInfo(clientId);
        Assert.assertNotNull("No channel info for client " + clientId, channelInfo);
        if (channelInfo.getLastMessageOn() == null) {
            Assert.assertTrue("Nothing written " + running + "ms after the first message, the flush interval is " + flushIntervalMillis + "ms",
                    running <= 2 * flushIntervalMillis + SLACK_MILLIS);
            return;
        }
        long on = assertConsistentPair(clientId, channelInfo);
        long lag = newestOn - on;
        long maxLag = minAdvanceMillis + 2 * flushIntervalMillis + SLACK_MILLIS;
        Assert.assertTrue("The last published message is " + lag + "ms behind the newest stored message, expected at most " + maxLag + "ms",
                lag <= maxLag);
    }

    /**
     * @return the stored lastMessageOn, after checking that it and lastMessageId belong to the same stored message.
     */
    private long assertConsistentPair(String clientId, ChannelInfo channelInfo) {
        long on = channelInfo.getLastMessageOn().getTime();
        String expectedId = timelineByClient.getOrDefault(clientId, new TreeMap<>()).get(on);
        Assert.assertNotNull("The stored lastMessageOn " + channelInfo.getLastMessageOn() + " is not the time of a stored message", expectedId);
        Assert.assertEquals("The stored lastMessageId does not belong to the stored time", expectedId, idOf(channelInfo.getLastMessageId()));
        return on;
    }

    private KapuaDataMessage newMessage(String clientId, String topic, Date capturedOn) {
        KapuaDataMessageFactory factory = KapuaLocator.getInstance().getFactory(KapuaDataMessageFactory.class);
        KapuaDataMessage message = factory.newKapuaDataMessage();
        message.setScopeId(((Account) stepData.get(LAST_ACCOUNT)).getId());
        message.setDeviceId(((Device) stepData.get(DEVICE)).getId());
        message.setClientId(clientId);
        message.setCapturedOn(capturedOn);
        message.setSentOn(capturedOn);
        message.setReceivedOn(capturedOn);
        KapuaDataChannel channel = factory.newKapuaDataChannel();
        channel.setSemanticParts(new ArrayList<>(Arrays.asList(topic.split("/"))));
        message.setChannel(channel);
        KapuaDataPayload payload = factory.newKapuaDataPayload();
        payload.getMetrics().put("counter", capturedOn.getTime());
        message.setPayload(payload);
        return message;
    }

    private KapuaDataMessage newest(String clientId) {
        KapuaDataMessage message = newestByClient.get(clientId);
        Assert.assertNotNull("No message of client " + clientId + " was stored through the tracker", message);
        return message;
    }

    /**
     * @param channel the channel, or {@code null} when the client has a single channel.
     */
    private void assertLastPublished(String clientId, String channel, KapuaDataMessage expected) throws Exception {
        ChannelInfo channelInfo = channelInfo(clientId, channel);
        Assert.assertNotNull("No channel info for client " + clientId + (channel != null ? " channel " + channel : ""), channelInfo);
        Assert.assertNotNull("No lastMessageOn in " + channelInfo.getName(), channelInfo.getLastMessageOn());
        Assert.assertEquals("Wrong lastMessageOn", expected.getCapturedOn(), channelInfo.getLastMessageOn());
        Assert.assertEquals("Wrong lastMessageId", storedIds.get(expected).toString(), idOf(channelInfo.getLastMessageId()));
    }

    private boolean matches(ChannelInfo channelInfo, KapuaDataMessage expected) {
        return expected.getCapturedOn().equals(channelInfo.getLastMessageOn())
                && storedIds.get(expected).toString().equals(idOf(channelInfo.getLastMessageId()));
    }

    private static String idOf(StorableId id) {
        return id == null ? null : id.toString();
    }

    private int trackerWritesOf(ChannelInfo channelInfo) {
        return writesByChannelInfoId.getOrDefault(channelInfo.getId().toString(), 0);
    }

    /**
     * @return the only channel info of the client in the current account, {@code null} when there is none.
     */
    private ChannelInfo channelInfo(String clientId) throws Exception {
        return channelInfo(clientId, null);
    }

    /**
     * @param channel the channel, or {@code null} to match any channel of the client (which must then have at most one).
     * @return the only matching channel info, read from the repository, {@code null} when there is none.
     */
    private ChannelInfo channelInfo(String clientId, String channel) throws Exception {
        refresh();
        ChannelInfoQueryImpl query = channelQuery(clientId, channel);
        query.setLimit(2);
        ChannelInfoListResult result = channelInfoRepository().query(query);
        Assert.assertTrue("More than one channel info for client " + clientId + (channel != null ? " channel " + channel : ""), result.getSize() <= 1);
        return result.getFirstItem();
    }

    private ChannelInfoQueryImpl channelQuery(String clientId, String channel) {
        DatastorePredicateFactory predicateFactory = KapuaLocator.getInstance().getFactory(DatastorePredicateFactory.class);
        AndPredicate and = predicateFactory.newAndPredicate();
        and.getPredicates().add(predicateFactory.newTermPredicate(ChannelInfoField.CLIENT_ID, clientId));
        if (channel != null) {
            and.getPredicates().add(predicateFactory.newTermPredicate(ChannelInfoField.CHANNEL, channel));
        }
        ChannelInfoQueryImpl query = new ChannelInfoQueryImpl(((Account) stepData.get(LAST_ACCOUNT)).getId());
        query.setPredicate(and);
        return query;
    }

    /**
     * Makes the latest channel info writes visible to reads (a no-op for stores that are not near real time). The channel indexes are refreshed
     * through the MESSAGE repository on purpose: every refresh of the channel info repository also clears its index cache, which for the info
     * repositories is the facade's channel cache, and a cleared channel cache would let the facade recreate a deleted channel doc.
     */
    private static void refresh() {
        KapuaLocator locator = KapuaLocator.getInstance();
        String channelIndexes = locator.getComponent(DatastoreUtils.class).getChannelIndexName(KapuaId.ANY);
        locator.getComponent(MessageRepository.class).refreshIndex(channelIndexes);
    }

    private static ChannelInfoRepository channelInfoRepository() {
        return KapuaLocator.getInstance().getComponent(ChannelInfoRepository.class);
    }

    /**
     * The channel as stored in the channel info doc ({@code a/b/c}).
     */
    private static String channelOf(KapuaDataMessage message) {
        return String.join("/", message.getChannel().getSemanticParts());
    }
}
