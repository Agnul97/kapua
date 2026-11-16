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
package org.eclipse.kapua.service.storeengine.client.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.Assertions;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.qa.markers.junit.JUnitTests;
import org.eclipse.kapua.service.storable.model.query.AbstractStorableQuery;
import org.eclipse.kapua.service.storable.model.query.SortField;
import org.eclipse.kapua.service.storable.model.query.StorableFetchStyle;
import org.eclipse.kapua.service.storeengine.client.QueryConverter;
import org.eclipse.kapua.service.storeengine.client.SchemaKeys;
import org.eclipse.kapua.service.storeengine.client.exception.QueryMappingException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests the {@code search_after} handling of {@link QueryConverterImpl}.
 */
@Category(JUnitTests.class)
public class QueryConverterSearchAfterTest {

    private QueryConverter converter;
    private TestQuery query;

    @Before
    public void setUp() {
        converter = new QueryConverterImpl();
        query = new TestQuery();
        query.setScopeId(KapuaId.ONE);
        query.setFetchStyle(StorableFetchStyle.FIELDS);
        query.setLimit(50);
        query.setSortFields(Arrays.asList(SortField.ascending("last_message_on"), SortField.ascending("channel")));
    }

    @Test
    public void searchAfterIsWrittenWithRawValues() throws Exception {
        query.setSearchAfter(Arrays.asList(1700000000000L, "a/b/c", 1.5d, true, null));
        query.setOffset(0);

        JsonNode node = converter.convertQuery(query);

        JsonNode searchAfter = node.get(SchemaKeys.KEY_SEARCH_AFTER);
        Assertions.assertThat(searchAfter).isNotNull();
        Assertions.assertThat(searchAfter.isArray()).isTrue();
        Assertions.assertThat(searchAfter.size()).isEqualTo(5);
        Assertions.assertThat(searchAfter.get(0).isNumber()).isTrue();
        Assertions.assertThat(searchAfter.get(0).asLong()).isEqualTo(1700000000000L);
        Assertions.assertThat(searchAfter.get(1).isTextual()).isTrue();
        Assertions.assertThat(searchAfter.get(1).asText()).isEqualTo("a/b/c");
        Assertions.assertThat(searchAfter.get(2).asDouble()).isEqualTo(1.5d);
        Assertions.assertThat(searchAfter.get(3).asBoolean()).isTrue();
        Assertions.assertThat(searchAfter.get(4).isNull()).isTrue();
        Assertions.assertThat(node.has(SchemaKeys.KEY_SORT)).isTrue();
        Assertions.assertThat(node.get(SchemaKeys.KEY_SIZE).asInt()).isEqualTo(50);
        Assertions.assertThat(node.toString()).contains("\"search_after\":[1700000000000,\"a/b/c\",1.5,true,null]");
    }

    @Test
    public void searchAfterIsAllowedWithNullOrZeroOffset() throws Exception {
        query.setSearchAfter(Collections.singletonList(1L));

        query.setOffset(null);
        Assertions.assertThat(converter.convertQuery(query).has(SchemaKeys.KEY_SEARCH_AFTER)).isTrue();

        query.setOffset(0);
        JsonNode node = converter.convertQuery(query);
        Assertions.assertThat(node.has(SchemaKeys.KEY_SEARCH_AFTER)).isTrue();
        Assertions.assertThat(node.get(SchemaKeys.KEY_FROM).asInt()).isZero(); // from=0 is accepted by ES together with search_after
    }

    @Test
    public void nullOrEmptySearchAfterIsNotWritten() throws Exception {
        query.setSearchAfter(null);
        Assertions.assertThat(converter.convertQuery(query).has(SchemaKeys.KEY_SEARCH_AFTER)).isFalse();

        query.setSearchAfter(Collections.emptyList());
        Assertions.assertThat(converter.convertQuery(query).has(SchemaKeys.KEY_SEARCH_AFTER)).isFalse();
    }

    @Test
    public void withoutSearchAfterOffsetKeepsWorking() throws Exception {
        query.setOffset(30);

        JsonNode node = converter.convertQuery(query);

        Assertions.assertThat(node.has(SchemaKeys.KEY_SEARCH_AFTER)).isFalse();
        Assertions.assertThat(node.get(SchemaKeys.KEY_FROM).asInt()).isEqualTo(30);
    }

    @Test
    public void searchAfterWithPositiveOffsetFailsFast() {
        query.setSearchAfter(Collections.singletonList(1L));
        query.setOffset(10);

        Assertions.assertThatThrownBy(() -> converter.convertQuery(query))
                .isInstanceOf(QueryMappingException.class)
                .hasMessageContaining("search_after")
                .hasMessageContaining("offset");
    }

    @Test
    public void searchAfterWithoutSortFailsFast() {
        query.setSearchAfter(Collections.singletonList(1L));

        query.setSortFields(null);
        Assertions.assertThatThrownBy(() -> converter.convertQuery(query))
                .isInstanceOf(QueryMappingException.class)
                .hasMessageContaining("sort");

        query.setSortFields(Collections.emptyList());
        Assertions.assertThatThrownBy(() -> converter.convertQuery(query))
                .isInstanceOf(QueryMappingException.class)
                .hasMessageContaining("sort");
    }

    @Test
    public void countAndDeleteIgnoreSearchAfter() throws Exception {
        query.setSearchAfter(Collections.singletonList(1L));
        query.setOffset(10); // would be invalid for a search: count and delete must not even look at it

        Assertions.assertThat(converter.convertCountQuery(query).has(SchemaKeys.KEY_SEARCH_AFTER)).isFalse();
        Assertions.assertThat(converter.convertDeleteQuery(query).has(SchemaKeys.KEY_SEARCH_AFTER)).isFalse();

        query.setSortFields(null); // same for the no sort case
        Assertions.assertThat(converter.convertCountQuery(query).has(SchemaKeys.KEY_SEARCH_AFTER)).isFalse();
        Assertions.assertThat(converter.convertDeleteQuery(query).has(SchemaKeys.KEY_SEARCH_AFTER)).isFalse();
    }

    private static class TestQuery extends AbstractStorableQuery {

        @Override
        public String[] getFields() {
            return new String[0];
        }

        @Override
        public String[] getIncludes(StorableFetchStyle fetchStyle) {
            return new String[0];
        }

        @Override
        public String[] getExcludes(StorableFetchStyle fetchStyle) {
            return new String[0];
        }
    }
}
