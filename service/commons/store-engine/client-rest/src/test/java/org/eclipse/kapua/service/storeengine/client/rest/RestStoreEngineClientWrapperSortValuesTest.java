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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.eclipse.kapua.qa.markers.junit.JUnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.List;

/**
 * Tests how {@link RestStoreEngineClientWrapper} reads the {@code sort} values of a hit.
 */
@Category(JUnitTests.class)
public class RestStoreEngineClientWrapperSortValuesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode hit(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    public void sortValuesAreReadRaw() throws Exception {
        List<Object> values = RestStoreEngineClientWrapper.readSortValues(objectMapper,
                hit("{\"_id\":\"1\",\"_source\":{},\"sort\":[1700000000000,\"a/b/c\",2.5,\"x\"]}"));

        Assertions.assertThat(values).containsExactly(1700000000000L, "a/b/c", 2.5d, "x");
    }

    @Test
    public void sortValuesRoundTripUnchanged() throws Exception {
        String sort = "[1700000000000,\"a/b/c\",7]";
        List<Object> values = RestStoreEngineClientWrapper.readSortValues(objectMapper, hit("{\"sort\":" + sort + "}"));

        Assertions.assertThat(objectMapper.writeValueAsString(values)).isEqualTo(sort);
    }

    @Test
    public void missingOrEmptySortGivesNull() throws Exception {
        Assertions.assertThat(RestStoreEngineClientWrapper.readSortValues(objectMapper, hit("{\"_id\":\"1\"}"))).isNull();
        Assertions.assertThat(RestStoreEngineClientWrapper.readSortValues(objectMapper, hit("{\"sort\":[]}"))).isNull();
        Assertions.assertThat(RestStoreEngineClientWrapper.readSortValues(objectMapper, hit("{\"sort\":null}"))).isNull();
        Assertions.assertThat(RestStoreEngineClientWrapper.readSortValues(objectMapper, null)).isNull();
    }

    @Test
    public void nullSortValueIsKept() throws Exception {
        List<Object> values = RestStoreEngineClientWrapper.readSortValues(objectMapper, hit("{\"sort\":[null,\"a\"]}"));

        Assertions.assertThat(values).isEqualTo(Arrays.asList(null, "a"));
    }
}
