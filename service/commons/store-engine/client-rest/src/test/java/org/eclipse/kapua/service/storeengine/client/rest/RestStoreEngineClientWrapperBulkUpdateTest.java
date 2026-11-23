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
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.assertj.core.api.Assertions;
import org.eclipse.kapua.qa.markers.junit.JUnitTests;
import org.eclipse.kapua.service.storable.model.Storable;
import org.eclipse.kapua.service.storeengine.client.ModelContext;
import org.eclipse.kapua.service.storeengine.client.model.BulkUpdateRequest;
import org.eclipse.kapua.service.storeengine.client.model.BulkUpdateResponse;
import org.eclipse.kapua.service.storeengine.client.model.UpdateRequest;
import org.eclipse.kapua.service.storeengine.client.model.UpdateResponse;
import org.eclipse.kapua.service.storeengine.client.rest.lowlevel.StoreEngineClient;
import org.eclipse.kapua.service.storeengine.client.rest.lowlevel.StoreEngineClientRequest;
import org.eclipse.kapua.service.storeengine.client.rest.lowlevel.StoreEngineClientResponse;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bulk upsert of {@link RestStoreEngineClientWrapper}: full requests keep {@code doc_as_upsert}, {@link UpdateRequest#partial} requests send only
 * their fields and never create a document, and every item response carries its HTTP status.
 */
@Category(JUnitTests.class)
public class RestStoreEngineClientWrapperBulkUpdateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Low level client that records the request body and answers with a canned bulk response.
     */
    private static class FakeClient implements StoreEngineClient {
        final String responseBody;
        String body;

        FakeClient(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public StoreEngineClientRequest newRequest(String method, String endpoint) {
            return new StoreEngineClientRequest() {
                @Override
                public void setJsonEntity(String json) {
                    body = json;
                }

                @Override
                public void addParameter(String name, String value) {
                }
            };
        }

        @Override
        public StoreEngineClientResponse performRequest(StoreEngineClientRequest request) {
            return new StoreEngineClientResponse() {
                @Override
                public int getStatusCode() {
                    return 200;
                }

                @Override
                public String getReasonPhrase() {
                    return "OK";
                }

                @Override
                public HttpEntity getEntity() {
                    return new StringEntity(responseBody, ContentType.APPLICATION_JSON);
                }
            };
        }

        @Override
        public void close() {
        }
    }

    /**
     * Marshals every storable to the same whole document, standing in for the real model context.
     */
    private static final ModelContext MODEL_CONTEXT = new ModelContext() {
        @Override
        public <T> T unmarshal(Class<T> clazz, Map<String, Object> serializedObject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> marshal(Object object) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("timestamp", "2026-01-01T00:00:00.000Z");
            doc.put("channel", "a/b");
            return doc;
        }

        @Override
        public String getIdKeyName() {
            return "id";
        }
    };

    private static String item(String index, String id, int status, String extra) {
        return "{\"update\":{\"_index\":\"" + index + "\",\"_id\":\"" + id + "\",\"status\":" + status + extra + "}}";
    }

    private BulkUpdateResponse send(FakeClient client, UpdateRequest... requests) throws Exception {
        RestStoreEngineClientWrapper wrapper = new RestStoreEngineClientWrapper(null);
        wrapper.withClient(client);
        wrapper.withModelContext(MODEL_CONTEXT);
        BulkUpdateRequest bulk = new BulkUpdateRequest();
        for (UpdateRequest r : requests) {
            bulk.add(r);
        }
        return wrapper.upsert(bulk);
    }

    @Test
    public void partialRequestSendsOnlyItsFieldsAndNeverUpserts() throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("last_message_on", "2026-11-19T10:15:30.123Z");
        doc.put("last_message_id", "m1");
        FakeClient client = new FakeClient("{\"errors\":false,\"items\":[" + item("1-data-channel", "abc", 200, ",\"result\":\"updated\"") + "]}");

        send(client, UpdateRequest.partial("abc", "1-data-channel", doc));

        String[] lines = client.body.split("\n");
        Assertions.assertThat(lines).hasSize(2);
        // the existing action line is not strict JSON (its outer object is never closed; ES accepts it), so it is checked as text
        Assertions.assertThat(lines[0]).startsWith("{ \"update\"").contains("\"_id\": \"abc\"").contains("\"_index\": \"1-data-channel\"");
        JsonNode source = objectMapper.readTree(lines[1]);
        Assertions.assertThat(source.has("doc_as_upsert")).isFalse();
        Assertions.assertThat(source.get("doc").size()).isEqualTo(2);
        Assertions.assertThat(source.at("/doc/last_message_on").asText()).isEqualTo("2026-11-19T10:15:30.123Z");
        Assertions.assertThat(source.at("/doc/last_message_id").asText()).isEqualTo("m1");
        Assertions.assertThat(source.has("timestamp")).isFalse();
    }

    @Test
    public void fullRequestStillUpsertsTheWholeDocument() throws Exception {
        Storable storable = (Storable) Proxy.newProxyInstance(Storable.class.getClassLoader(), new Class<?>[]{Storable.class}, (p, m, a) -> null);
        FakeClient client = new FakeClient("{\"errors\":false,\"items\":[" + item("1-data-channel", "abc", 201, ",\"result\":\"created\"") + "]}");

        send(client, new UpdateRequest("abc", "1-data-channel", storable));

        JsonNode source = objectMapper.readTree(client.body.split("\n")[1]);
        Assertions.assertThat(source.get("doc_as_upsert").asBoolean()).isTrue();
        Assertions.assertThat(source.at("/doc/timestamp").asText()).isEqualTo("2026-01-01T00:00:00.000Z");
        Assertions.assertThat(source.at("/doc/channel").asText()).isEqualTo("a/b");
    }

    @Test
    public void itemResponsesCarryTheirStatusInOrder() throws Exception {
        Map<String, Object> doc = Collections.singletonMap("last_message_id", "m1");
        FakeClient client = new FakeClient("{\"errors\":true,\"items\":["
                + item("i", "1", 200, ",\"result\":\"updated\"") + ","
                + item("i", "2", 404, ",\"error\":{\"type\":\"document_missing_exception\",\"reason\":\"[2]: document missing\"}") + ","
                + item("i", "3", 409, ",\"error\":{\"type\":\"version_conflict_engine_exception\",\"reason\":\"conflict\"}")
                + "]}");

        List<UpdateResponse> responses = send(client,
                UpdateRequest.partial("1", "i", doc), UpdateRequest.partial("2", "i", doc), UpdateRequest.partial("3", "i", doc)).getResponse();

        Assertions.assertThat(responses).extracting(UpdateResponse::getId).containsExactly("1", "2", "3");
        Assertions.assertThat(responses).extracting(UpdateResponse::getStatus).containsExactly(200, 404, 409);
        Assertions.assertThat(responses.get(0).isResult()).isTrue();
        Assertions.assertThat(responses.get(1).isResult()).isFalse();
        Assertions.assertThat(responses.get(1).getDescription()).isEqualTo("[2]: document missing");
    }
}
