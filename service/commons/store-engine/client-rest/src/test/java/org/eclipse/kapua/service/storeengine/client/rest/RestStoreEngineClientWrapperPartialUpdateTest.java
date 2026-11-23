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
import org.eclipse.kapua.service.storeengine.client.exception.ClientInternalError;
import org.eclipse.kapua.service.storeengine.client.model.PartialUpdateRequest;
import org.eclipse.kapua.service.storeengine.client.model.PartialUpdateResponse;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests the NDJSON body and the response parsing of {@link RestStoreEngineClientWrapper#partialUpdate(List)}.
 */
@Category(JUnitTests.class)
public class RestStoreEngineClientWrapperPartialUpdateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void docBodyHasActionAndDocLinesWithoutUpsert() throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("last_message_on", "2026-11-19T10:15:30.123Z");
        doc.put("last_message_id", "m1");
        String body = RestStoreEngineClientWrapper.buildPartialUpdateBulkBody(objectMapper, Collections.singletonList(
                new PartialUpdateRequest("1-data-channel", "abc", doc, 3)));

        Assertions.assertThat(body).endsWith("\n").doesNotContain("upsert");
        String[] lines = body.split("\n");
        Assertions.assertThat(lines).hasSize(2);
        JsonNode action = objectMapper.readTree(lines[0]);
        Assertions.assertThat(action.at("/update/_index").asText()).isEqualTo("1-data-channel");
        Assertions.assertThat(action.at("/update/_id").asText()).isEqualTo("abc");
        Assertions.assertThat(action.at("/update/retry_on_conflict").asInt()).isEqualTo(3);
        JsonNode source = objectMapper.readTree(lines[1]);
        Assertions.assertThat(source.at("/doc/last_message_on").asText()).isEqualTo("2026-11-19T10:15:30.123Z");
        Assertions.assertThat(source.at("/doc/last_message_id").asText()).isEqualTo("m1");
    }

    @Test
    public void responseItemsAreReadInOrder() throws Exception {
        JsonNode response = objectMapper.readTree("{\"took\":3,\"errors\":true,\"items\":["
                + "{\"update\":{\"_index\":\"i\",\"_id\":\"1\",\"status\":200,\"result\":\"updated\"}},"
                + "{\"update\":{\"_index\":\"i\",\"_id\":\"2\",\"status\":200,\"result\":\"noop\"}},"
                + "{\"update\":{\"_index\":\"i\",\"_id\":\"3\",\"status\":404,\"error\":{\"type\":\"document_missing_exception\",\"reason\":\"[3]: document missing\"}}},"
                + "{\"update\":{\"_index\":\"i\",\"_id\":\"4\",\"status\":429,\"error\":{\"type\":\"es_rejected_execution_exception\",\"reason\":\"rejected\"}}}"
                + "]}");

        List<PartialUpdateResponse> items = RestStoreEngineClientWrapper.readPartialUpdateBulkResponse(response, 4);

        Assertions.assertThat(items).extracting(PartialUpdateResponse::getId).containsExactly("1", "2", "3", "4");
        Assertions.assertThat(items).extracting(PartialUpdateResponse::getStatus).containsExactly(200, 200, 404, 429);
        Assertions.assertThat(items).extracting(PartialUpdateResponse::getResult).containsExactly("updated", "noop", null, null);
        Assertions.assertThat(items.get(2).getErrorType()).isEqualTo("document_missing_exception");
        Assertions.assertThat(items.get(0).isSuccessful()).isTrue();
        Assertions.assertThat(items.get(2).isSuccessful()).isFalse();
    }

    @Test
    public void responseWithWrongItemCountIsRejected() throws Exception {
        JsonNode response = new ObjectMapper().readTree("{\"errors\":false,\"items\":["
                + "{\"update\":{\"_index\":\"i\",\"_id\":\"1\",\"status\":200,\"result\":\"updated\"}}"
                + "]}");

        Assertions.assertThatThrownBy(() -> RestStoreEngineClientWrapper.readPartialUpdateBulkResponse(response, 2))
                .isInstanceOf(ClientInternalError.class)
                .hasMessageContaining("1 items, 2 requests");
    }
}
