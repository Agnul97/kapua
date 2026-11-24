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
import org.eclipse.kapua.service.datastore.internal.DatastoreCacheManager;
import org.eclipse.kapua.service.datastore.internal.MetricInfoFactoryImpl;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedKey;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedResult.Outcome;
import org.eclipse.kapua.service.datastore.internal.lastpublished.LastPublishedUpdate;
import org.eclipse.kapua.service.datastore.internal.mediator.ChannelInfoField;
import org.eclipse.kapua.service.datastore.internal.mediator.DatastoreUtils;
import org.eclipse.kapua.service.datastore.internal.mediator.MetricInfoField;
import org.eclipse.kapua.service.datastore.internal.model.ChannelInfoImpl;
import org.eclipse.kapua.service.datastore.internal.model.MetricInfoImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.ChannelInfoQueryImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.MetricInfoQueryImpl;
import org.eclipse.kapua.service.datastore.internal.model.query.predicate.DatastorePredicateFactoryImpl;
import org.eclipse.kapua.service.datastore.internal.setting.DatastoreSettings;
import org.eclipse.kapua.service.datastore.model.ChannelInfo;
import org.eclipse.kapua.service.datastore.model.ChannelInfoListResult;
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
}
