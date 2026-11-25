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
package org.eclipse.kapua.service.datastore.internal.jpa;

import org.eclipse.kapua.commons.jpa.JdbcConnectionUrlResolvers;
import org.eclipse.kapua.commons.jpa.KapuaJpaRepositoryConfiguration;
import org.eclipse.kapua.commons.jpa.KapuaJpaTxManagerFactory;
import org.eclipse.kapua.commons.liquibase.KapuaLiquibaseClient;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.setting.system.SystemSetting;
import org.eclipse.kapua.commons.setting.system.SystemSettingKey;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.qa.markers.junit.JUnitTests;
import org.eclipse.kapua.service.datastore.internal.ChannelInfoFactoryImpl;
import org.eclipse.kapua.service.datastore.internal.ClientInfoFactoryImpl;
import org.eclipse.kapua.service.datastore.internal.DatastoreCacheManager;
import org.eclipse.kapua.service.datastore.internal.MetricInfoFactoryImpl;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedKey;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult.Outcome;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedUpdate;
import org.eclipse.kapua.service.datastore.internal.mediator.ChannelInfoField;
import org.eclipse.kapua.service.datastore.internal.mediator.ClientInfoField;
import org.eclipse.kapua.service.datastore.internal.mediator.DatastoreUtils;
import org.eclipse.kapua.service.datastore.internal.mediator.MessageField;
import org.eclipse.kapua.service.datastore.internal.mediator.MetricInfoField;
import org.eclipse.kapua.service.datastore.internal.model.ChannelInfoImpl;
import org.eclipse.kapua.service.datastore.internal.model.ClientInfoImpl;
import org.eclipse.kapua.service.datastore.internal.model.MetricInfoImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.ChannelInfoQueryImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.ClientInfoQueryImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.MetricInfoQueryImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.predicate.ChannelMatchPredicateImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.predicate.DatastorePredicateFactoryImpl;
import org.eclipse.kapua.service.datastore.internal.schema.MessageSchema;
import org.eclipse.kapua.service.datastore.internal.setting.DatastoreSettings;
import org.eclipse.kapua.service.datastore.model.ChannelInfo;
import org.eclipse.kapua.service.datastore.model.ChannelInfoListResult;
import org.eclipse.kapua.service.datastore.model.ClientInfoListResult;
import org.eclipse.kapua.service.datastore.model.MetricInfo;
import org.eclipse.kapua.service.datastore.model.MetricInfoListResult;
import org.eclipse.kapua.service.storable.model.id.StorableIdImpl;
import org.eclipse.kapua.service.storable.model.query.SortField;
import org.eclipse.kapua.service.storable.model.query.predicate.AndPredicate;
import org.eclipse.kapua.service.storable.model.query.predicate.IdsPredicate;
import org.eclipse.kapua.storage.TxManager;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests the JPA info repositories against the H2 database of the tests, with the tables created by the datastore Liquibase changelog.
 */
@Category(JUnitTests.class)
public class InfoJpaRepositoryTest {

    private static final KapuaId SCOPE = new KapuaEid(BigInteger.valueOf(42));
    private static final KapuaId OTHER_SCOPE = new KapuaEid(BigInteger.valueOf(43));

    // keeps the in-memory H2 database alive for the whole class
    private static Connection keepAlive;
    private static ChannelInfoJpaRepository channels;
    private static ClientInfoJpaRepository clients;
    private static MetricInfoJpaRepository metrics;
    private static DatastoreCacheManager cacheManager;
    private static DatastorePredicateFactoryImpl predicates;

    @BeforeClass
    public static void setUp() throws Exception {
        SystemSetting config = SystemSetting.getInstance();
        String user = config.getString(SystemSettingKey.DB_USERNAME);
        String password = config.getString(SystemSettingKey.DB_PASSWORD);
        String schema = config.getString(SystemSettingKey.DB_SCHEMA);
        String jdbcUrl = JdbcConnectionUrlResolvers.resolveJdbcUrl();
        keepAlive = DriverManager.getConnection(jdbcUrl, user, password);
        new KapuaLiquibaseClient(jdbcUrl, user, password, schema).update();

        DatastoreSettings datastoreSettings = new DatastoreSettings();
        TxManager txManager = new KapuaJpaTxManagerFactory(3).create("kapua-datastore");
        cacheManager = new DatastoreCacheManager(datastoreSettings);
        channels = new ChannelInfoJpaRepository(new ChannelInfoFactoryImpl(), txManager, new KapuaJpaRepositoryConfiguration(), cacheManager);
        clients = new ClientInfoJpaRepository(new ClientInfoFactoryImpl(), txManager, new KapuaJpaRepositoryConfiguration(), cacheManager);
        metrics = new MetricInfoJpaRepository(new MetricInfoFactoryImpl(), txManager, new KapuaJpaRepositoryConfiguration(), cacheManager);
        predicates = new DatastorePredicateFactoryImpl(new DatastoreUtils(datastoreSettings));
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (keepAlive != null) {
            keepAlive.close();
        }
    }

    @Before
    public void clean() {
        channels.deleteAllIndexes();
        clients.deleteAllIndexes();
        metrics.deleteAllIndexes();
    }

    private static ChannelInfoImpl channel(KapuaId scope, String id, String clientId, String name, long firstMessageOn) {
        ChannelInfoImpl channel = new ChannelInfoImpl(scope);
        channel.setId(new StorableIdImpl(id));
        channel.setClientId(clientId);
        channel.setName(name);
        channel.setFirstMessageId(new StorableIdImpl("msg-" + id));
        channel.setFirstMessageOn(new Date(firstMessageOn));
        return channel;
    }

    private static List<String> ids(ChannelInfoListResult result) {
        return result.getItems().stream().map(c -> c.getId().toString()).collect(Collectors.toList());
    }

    @Test
    public void upsertThenFindRespectsScope() {
        channels.upsert("c1", channel(SCOPE, "c1", "client-1", "a/b", 1_000));

        ChannelInfo found = channels.find(SCOPE, new StorableIdImpl("c1"));
        Assert.assertNotNull(found);
        Assert.assertEquals("client-1", found.getClientId());
        Assert.assertEquals("a/b", found.getName());
        Assert.assertEquals(new Date(1_000), found.getFirstMessageOn());
        Assert.assertEquals("msg-c1", found.getFirstMessageId().toString());
        Assert.assertEquals(SCOPE.getId(), found.getScopeId().getId());

        Assert.assertNull("another scope does not see it", channels.find(OTHER_SCOPE, new StorableIdImpl("c1")));
        Assert.assertNotNull("ANY sees every scope", channels.find(KapuaId.ANY, new StorableIdImpl("c1")));
        Assert.assertNull(channels.find(SCOPE, new StorableIdImpl("missing")));
    }

    @Test
    public void upsertOfAnExistingItemUpdatesIt() {
        channels.upsert("c1", channel(SCOPE, "c1", "client-1", "a/b", 1_000));
        ChannelInfoImpl updated = channel(SCOPE, "c1", "client-1", "a/b", 1_000);
        updated.setFirstMessageId(new StorableIdImpl("msg-other"));
        channels.upsert("c1", updated);

        Assert.assertEquals("msg-other", channels.find(SCOPE, new StorableIdImpl("c1")).getFirstMessageId().toString());
        Assert.assertEquals(1, channels.count(new ChannelInfoQueryImpl(SCOPE)));
    }

    @Test
    public void queryFiltersSortsAndLimits() {
        channels.upsert(Arrays.asList(
                channel(SCOPE, "c1", "client-1", "a/b", 1_000),
                channel(SCOPE, "c2", "client-1", "a/c", 2_000),
                channel(SCOPE, "c3", "client-1", "x/y", 3_000),
                channel(SCOPE, "c4", "client-2", "a/d", 4_000),
                channel(OTHER_SCOPE, "c5", "client-1", "a/e", 5_000)));

        ChannelInfoQueryImpl query = new ChannelInfoQueryImpl(SCOPE);
        AndPredicate and = predicates.newAndPredicate();
        and.getPredicates().add(predicates.newTermPredicate(ChannelInfoField.CLIENT_ID, "client-1"));
        and.getPredicates().add(predicates.newChannelMatchPredicate("a/"));
        query.setPredicate(and);
        query.setSortFields(Collections.singletonList(SortField.descending(ChannelInfoField.TIMESTAMP.field())));
        query.setLimit(1);

        ChannelInfoListResult result = channels.query(query);
        Assert.assertEquals(Collections.singletonList("c2"), ids(result));
        Assert.assertTrue(result.isLimitExceeded());
        Assert.assertEquals(Long.valueOf(2), result.getTotalCount());

        query.setLimit(10);
        result = channels.query(query);
        Assert.assertEquals(Arrays.asList("c2", "c1"), ids(result));
        Assert.assertFalse(result.isLimitExceeded());

        ChannelInfoQueryImpl anyScope = new ChannelInfoQueryImpl(KapuaId.ANY);
        anyScope.setPredicate(predicates.newTermPredicate(ChannelInfoField.CLIENT_ID, "client-1"));
        Assert.assertEquals(4, channels.count(anyScope));
    }

    @Test
    public void rangeExistsAndIdsPredicates() {
        channels.upsert(Arrays.asList(
                channel(SCOPE, "c1", "client-1", "a/b", 1_000),
                channel(SCOPE, "c2", "client-1", "a/c", 2_000),
                channel(SCOPE, "c3", "client-1", "a/d", 3_000)));

        ChannelInfoQueryImpl range = new ChannelInfoQueryImpl(SCOPE);
        range.setPredicate(predicates.newRangePredicate(ChannelInfoField.TIMESTAMP, new Date(2_000), new Date(3_000)));
        Assert.assertEquals(new HashSet<>(Arrays.asList("c2", "c3")), new HashSet<>(ids(channels.query(range))));

        ChannelInfoQueryImpl exists = new ChannelInfoQueryImpl(SCOPE);
        exists.setPredicate(predicates.newExistsPredicate(InfoFieldMapping.LAST_MESSAGE_ON_FIELD));
        Assert.assertEquals("no item has a last message yet", 0, channels.count(exists));

        ChannelInfoQueryImpl byIds = new ChannelInfoQueryImpl(SCOPE);
        IdsPredicate idsPredicate = predicates.newIdsPredicate();
        idsPredicate.addId(new StorableIdImpl("c1"));
        idsPredicate.addId(new StorableIdImpl("c3"));
        byIds.setPredicate(idsPredicate);
        Assert.assertEquals(new HashSet<>(Arrays.asList("c1", "c3")), new HashSet<>(ids(channels.query(byIds))));
    }

    @Test
    public void deleteByQueryAndById() {
        channels.upsert(Arrays.asList(
                channel(SCOPE, "c1", "client-1", "a/b", 1_000),
                channel(SCOPE, "c2", "client-2", "a/c", 2_000),
                channel(OTHER_SCOPE, "c3", "client-1", "a/d", 3_000)));

        ChannelInfoQueryImpl byClient = new ChannelInfoQueryImpl(SCOPE);
        byClient.setPredicate(predicates.newTermPredicate(ChannelInfoField.CLIENT_ID, "client-1"));
        channels.delete(byClient);
        Assert.assertNull(channels.find(SCOPE, new StorableIdImpl("c1")));
        Assert.assertNotNull("other scope untouched", channels.find(OTHER_SCOPE, new StorableIdImpl("c3")));

        channels.delete(OTHER_SCOPE, new StorableIdImpl("c2"));
        Assert.assertNotNull("delete by id respects the scope", channels.find(SCOPE, new StorableIdImpl("c2")));
        channels.delete(SCOPE, new StorableIdImpl("c2"));
        Assert.assertNull(channels.find(SCOPE, new StorableIdImpl("c2")));
    }

    @Test
    public void deleteAllIndexesEmptiesTheTableAndTheItemsCache() {
        channels.upsert("c1", channel(SCOPE, "c1", "client-1", "a/b", 1_000));
        cacheManager.getChannelsCache().put("c1", true);

        channels.deleteAllIndexes();

        Assert.assertEquals(0, channels.count(new ChannelInfoQueryImpl(KapuaId.ANY)));
        Assert.assertFalse(cacheManager.getChannelsCache().get("c1"));
    }

    @Test
    public void metricTypeAndNameRoundTripAndQuery() {
        MetricInfoImpl metric = new MetricInfoImpl(SCOPE);
        metric.setId(new StorableIdImpl("m1"));
        metric.setClientId("client-1");
        metric.setChannel("a/b");
        metric.setName("temperature");
        metric.setMetricType(Double.class);
        metric.setFirstMessageId(new StorableIdImpl("msg-1"));
        metric.setFirstMessageOn(new Date(1_000));
        metrics.upsert(Collections.singletonList(metric));

        MetricInfo found = metrics.find(SCOPE, new StorableIdImpl("m1"));
        Assert.assertEquals(Double.class, found.getMetricType());
        Assert.assertEquals("temperature", found.getName());

        MetricInfoQueryImpl query = new MetricInfoQueryImpl(SCOPE);
        AndPredicate and = predicates.newAndPredicate();
        and.getPredicates().add(predicates.newTermPredicate(MetricInfoField.NAME_FULL, "temperature"));
        and.getPredicates().add(predicates.newTermPredicate(MetricInfoField.TYPE_FULL, DatastoreUtils.convertToClientMetricType(Double.class)));
        query.setPredicate(and);
        MetricInfoListResult result = metrics.query(query);
        Assert.assertEquals(1, result.getSize());
    }

    private static LastPublishedUpdate lastPublished(String id, long onMillis, String messageId) {
        return new LastPublishedUpdate(new LastPublishedKey(SCOPE, id), onMillis, messageId);
    }

    @Test
    public void updateLastPublishedWritesBothFieldsOnAnEmptyValue() throws Exception {
        channels.upsert("c1", channel(SCOPE, "c1", "client-1", "a/b", 1_000));

        LastPublishedResult result = channels.updateLastPublished(Collections.singletonList(lastPublished("c1", 5_000, "m5")), 1_000);

        Assert.assertEquals(Outcome.DONE, result.get(new LastPublishedKey(SCOPE, "c1")));
        ChannelInfo found = channels.find(SCOPE, new StorableIdImpl("c1"));
        Assert.assertEquals(new Date(5_000), found.getLastMessageOn());
        Assert.assertEquals("m5", found.getLastMessageId().toString());
    }

    @Test
    public void updateLastPublishedNeverMovesBackwardsAndHonoursTheMinimumAdvance() throws Exception {
        channels.upsert("c1", channel(SCOPE, "c1", "client-1", "a/b", 1_000));
        channels.updateLastPublished(Collections.singletonList(lastPublished("c1", 10_000, "m10")), 1_000);

        // older value: skipped, reported DONE
        Assert.assertEquals(Outcome.DONE, channels.updateLastPublished(Collections.singletonList(lastPublished("c1", 5_000, "m5")), 1_000)
                .get(new LastPublishedKey(SCOPE, "c1")));
        // newer, but by less than the minimum advance: skipped
        channels.updateLastPublished(Collections.singletonList(lastPublished("c1", 10_500, "m10.5")), 1_000);
        ChannelInfo found = channels.find(SCOPE, new StorableIdImpl("c1"));
        Assert.assertEquals(new Date(10_000), found.getLastMessageOn());
        Assert.assertEquals("m10", found.getLastMessageId().toString());

        // advance exactly equal to the minimum: written
        channels.updateLastPublished(Collections.singletonList(lastPublished("c1", 11_000, "m11")), 1_000);
        found = channels.find(SCOPE, new StorableIdImpl("c1"));
        Assert.assertEquals(new Date(11_000), found.getLastMessageOn());
        Assert.assertEquals("m11", found.getLastMessageId().toString());
    }

    @Test
    public void updateLastPublishedReportsMissingItemsAndNeverCreatesThem() throws Exception {
        channels.upsert("c1", channel(SCOPE, "c1", "client-1", "a/b", 1_000));
        channels.updateLastPublished(Collections.singletonList(lastPublished("c1", 10_000, "m10")), 1_000);

        LastPublishedResult result = channels.updateLastPublished(Arrays.asList(
                lastPublished("c1", 10_100, "skipped"),   // exists, advance below the minimum
                lastPublished("missing", 20_000, "m20")), 1_000);

        Assert.assertEquals(Outcome.DONE, result.get(new LastPublishedKey(SCOPE, "c1")));
        Assert.assertEquals(Outcome.MISSING, result.get(new LastPublishedKey(SCOPE, "missing")));
        Assert.assertNull("an update never creates an item", channels.find(SCOPE, new StorableIdImpl("missing")));
        Assert.assertEquals(1, channels.count(new ChannelInfoQueryImpl(SCOPE)));
    }

    @Test
    public void updateLastPublishedWorksForMetrics() throws Exception {
        MetricInfoImpl metric = new MetricInfoImpl(SCOPE);
        metric.setId(new StorableIdImpl("m1"));
        metric.setClientId("client-1");
        metric.setChannel("a/b");
        metric.setName("temperature");
        metric.setMetricType(Double.class);
        metric.setFirstMessageId(new StorableIdImpl("msg-1"));
        metric.setFirstMessageOn(new Date(1_000));
        metrics.upsert(Collections.singletonList(metric));

        LastPublishedResult result = metrics.updateLastPublished(Collections.singletonList(lastPublished("m1", 7_000, "m7")), 0);

        Assert.assertEquals(Outcome.DONE, result.get(new LastPublishedKey(SCOPE, "m1")));
        Assert.assertEquals(new Date(7_000), metrics.find(SCOPE, new StorableIdImpl("m1")).getLastMessageOn());
    }

    // Queries made by the console data module (GwtDataServiceImpl), run on SQL

    private static ClientInfoImpl client(String id, String clientId, long firstMessageOn) {
        ClientInfoImpl client = new ClientInfoImpl(SCOPE);
        client.setId(new StorableIdImpl(id));
        client.setClientId(clientId);
        client.setFirstMessageId(new StorableIdImpl("msg-" + id));
        client.setFirstMessageOn(new Date(firstMessageOn));
        return client;
    }

    private static MetricInfoImpl metric(String id, String clientId, String channel, String name) {
        MetricInfoImpl metric = new MetricInfoImpl(SCOPE);
        metric.setId(new StorableIdImpl(id));
        metric.setClientId(clientId);
        metric.setChannel(channel);
        metric.setName(name);
        metric.setMetricType(Double.class);
        metric.setFirstMessageId(new StorableIdImpl("msg-" + id));
        metric.setFirstMessageOn(new Date(1_000));
        return metric;
    }

    private static List<String> metricNames(MetricInfoListResult result) {
        return result.getItems().stream().map(MetricInfo::getName).sorted().collect(Collectors.toList());
    }

    @Test
    public void consoleTopicsTreeAndTopicsList() {
        channels.upsert(Arrays.asList(
                channel(SCOPE, "c1", "client-1", "a/b", 1_000),
                channel(SCOPE, "c2", "client-1", "a/c", 2_000),
                channel(SCOPE, "c3", "client-2", "x/y", 3_000),
                channel(SCOPE, "c4", "client-2", "x/z", 4_000),
                channel(SCOPE, "c5", "client-3", "w", 5_000)));

        // findTopicsTree: pages without sort until an empty page; every channel exactly once
        ChannelInfoQueryImpl tree = new ChannelInfoQueryImpl(SCOPE);
        tree.setLimit(2);
        List<String> seen = new ArrayList<>();
        int offset = 0;
        tree.setOffset(offset);
        ChannelInfoListResult page = channels.query(tree);
        while (!page.isEmpty()) {
            seen.addAll(ids(page));
            offset += 2;
            tree.setOffset(offset);
            page = channels.query(tree);
        }
        Assert.assertEquals(5, seen.size());
        Assert.assertEquals(new HashSet<>(Arrays.asList("c1", "c2", "c3", "c4", "c5")), new HashSet<>(seen));

        // findTopicsList: one page, then the count with limit -1 and offset 0
        ChannelInfoQueryImpl list = new ChannelInfoQueryImpl(SCOPE);
        list.setOffset(0);
        list.setLimit(2);
        Assert.assertEquals(2, channels.query(list).getSize());
        list.setLimit(-1);
        Assert.assertEquals(5, channels.count(list));
    }

    @Test
    public void consoleDevicesFilterAndSort() {
        clients.upsert(Arrays.asList(
                client("d1", "dev-1", 1_000),
                client("d2", "dev-2", 3_000),
                client("d3", "gateway-1", 2_000)));

        // findDevices: ChannelMatchPredicate on client_id (prefix), sorted on timestamp, with the total count
        ClientInfoQueryImpl devices = new ClientInfoQueryImpl(SCOPE);
        devices.setPredicate(new ChannelMatchPredicateImpl(ClientInfoField.CLIENT_ID, "dev"));
        devices.setAskTotalCount(true);
        devices.setOffset(0);
        devices.setLimit(10);
        devices.setSortFields(Collections.singletonList(SortField.descending(ClientInfoField.TIMESTAMP.field())));
        ClientInfoListResult result = clients.query(devices);
        Assert.assertEquals(Arrays.asList("dev-2", "dev-1"), result.getItems().stream().map(c -> c.getClientId()).collect(Collectors.toList()));
        Assert.assertEquals(Long.valueOf(2), result.getTotalCount());

        // first message of a device: Term on client_id, sorted on the message timestamp field, limit 1
        ClientInfoQueryImpl firstMessage = new ClientInfoQueryImpl(SCOPE);
        firstMessage.setPredicate(predicates.newTermPredicate(ClientInfoField.CLIENT_ID, "gateway-1"));
        firstMessage.setLimit(1);
        firstMessage.setOffset(0);
        firstMessage.setAskTotalCount(true);
        firstMessage.setSortFields(Collections.singletonList(SortField.descending(MessageSchema.MESSAGE_TIMESTAMP)));
        firstMessage.setFetchAttributes(Collections.singletonList(ClientInfoField.TIMESTAMP.field()));
        Assert.assertEquals(new Date(2_000), clients.query(firstMessage).getFirstItem().getFirstMessageOn());
    }

    @Test
    public void consoleHeadersOfTopicDeviceAndAsset() {
        metrics.upsert(Arrays.asList(
                metric("m1", "dev-1", "a/b", "temperature"),
                metric("m2", "dev-1", "a/b/c", "humidity"),
                metric("m3", "dev-2", "a/x", "pressure"),
                metric("m4", "dev-2", "z/b", "voltage")));

        // findHeaders(topic) without "/#": Term on the message channel field
        MetricInfoQueryImpl byTopic = new MetricInfoQueryImpl(SCOPE);
        byTopic.setLimit(10000);
        byTopic.setPredicate(predicates.newTermPredicate(MessageField.CHANNEL, "a/b"));
        Assert.assertEquals(Collections.singletonList("temperature"), metricNames(metrics.query(byTopic)));

        // findHeaders(topic) with "/#", and findHeaders(asset): ChannelMatchPredicate on the default (message channel) field
        MetricInfoQueryImpl byTopicTree = new MetricInfoQueryImpl(SCOPE);
        byTopicTree.setLimit(10000);
        byTopicTree.setPredicate(predicates.newChannelMatchPredicate("a/b/#".replaceFirst("/#$", "/")));
        Assert.assertEquals(Collections.singletonList("humidity"), metricNames(metrics.query(byTopicTree)));

        // findHeaders(device): Term on the metric client_id
        MetricInfoQueryImpl byDevice = new MetricInfoQueryImpl(SCOPE);
        byDevice.setLimit(10000);
        byDevice.setPredicate(predicates.newTermPredicate(MetricInfoField.CLIENT_ID, "dev-2"));
        Assert.assertEquals(Arrays.asList("pressure", "voltage"), metricNames(metrics.query(byDevice)));
    }

    @Test
    public void channelMatchTreatsLikeWildcardsLiterally() {
        channels.upsert(Arrays.asList(
                channel(SCOPE, "c1", "client-1", "a_b/x", 1_000),
                channel(SCOPE, "c2", "client-1", "aXb/x", 1_000),
                channel(SCOPE, "c3", "client-1", "100%/x", 1_000),
                channel(SCOPE, "c4", "client-1", "1000/x", 1_000)));

        ChannelInfoQueryImpl underscore = new ChannelInfoQueryImpl(SCOPE);
        underscore.setPredicate(predicates.newChannelMatchPredicate("a_b/"));
        Assert.assertEquals(Collections.singletonList("c1"), ids(channels.query(underscore)));

        ChannelInfoQueryImpl percent = new ChannelInfoQueryImpl(SCOPE);
        percent.setPredicate(predicates.newChannelMatchPredicate("100%"));
        Assert.assertEquals(Collections.singletonList("c3"), ids(channels.query(percent)));
    }

    @Test
    public void aQueryWithoutLimitReturnsTheDefaultLimit() {
        List<ChannelInfo> many = new ArrayList<>();
        for (int i = 0; i < StorableJpaRepository.DEFAULT_QUERY_LIMIT + 2; i++) {
            many.add(channel(SCOPE, "c" + i, "client-1", "a/" + i, 1_000));
        }
        channels.upsert(many);

        ChannelInfoQueryImpl noLimit = new ChannelInfoQueryImpl(SCOPE);
        Assert.assertNull(noLimit.getLimit());
        ChannelInfoListResult result = channels.query(noLimit);
        Assert.assertEquals(StorableJpaRepository.DEFAULT_QUERY_LIMIT, result.getSize());
        Assert.assertTrue(result.isLimitExceeded());
        Assert.assertEquals(Long.valueOf(StorableJpaRepository.DEFAULT_QUERY_LIMIT + 2), result.getTotalCount());
    }

    @Test
    public void messageOnlyMetricPredicatesAreRefused() {
        ChannelInfoQueryImpl metricRange = new ChannelInfoQueryImpl(SCOPE);
        metricRange.setPredicate(predicates.newMetricPredicate("temperature", Double.class, 1.0, 2.0));
        try {
            channels.query(metricRange);
            Assert.fail("MetricPredicate must be refused");
        } catch (UnsupportedOperationException expected) {
            // ok
        }

        // a metric named like an info field ("channel") must not become "channel IS NOT NULL"
        ChannelInfoQueryImpl metricExists = new ChannelInfoQueryImpl(SCOPE);
        metricExists.setPredicate(predicates.newMetricExistsPredicate("channel", Double.class));
        try {
            channels.count(metricExists);
            Assert.fail("MetricExistsPredicate must be refused");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}
