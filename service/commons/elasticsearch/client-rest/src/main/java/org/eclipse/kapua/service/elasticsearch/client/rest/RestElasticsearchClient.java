/*******************************************************************************
 * Copyright (c) 2017, 2020 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.elasticsearch.client.rest;

import com.codahale.metrics.Counter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.eclipse.kapua.commons.metric.MetricServiceFactory;
import org.eclipse.kapua.commons.metric.MetricsService;
import org.eclipse.kapua.commons.util.RandomUtils;
import org.eclipse.kapua.service.elasticsearch.client.AbstractElasticsearchClient;
import org.eclipse.kapua.service.elasticsearch.client.ModelContext;
import org.eclipse.kapua.service.elasticsearch.client.QueryConverter;
import org.eclipse.kapua.service.elasticsearch.client.SchemaKeys;
import org.eclipse.kapua.service.elasticsearch.client.exception.ClientActionResponseException;
import org.eclipse.kapua.service.elasticsearch.client.exception.ClientCommunicationException;
import org.eclipse.kapua.service.elasticsearch.client.exception.ClientErrorCodes;
import org.eclipse.kapua.service.elasticsearch.client.exception.ClientException;
import org.eclipse.kapua.service.elasticsearch.client.exception.ClientInitializationException;
import org.eclipse.kapua.service.elasticsearch.client.exception.ClientInternalError;
import org.eclipse.kapua.service.elasticsearch.client.model.BulkUpdateRequest;
import org.eclipse.kapua.service.elasticsearch.client.model.BulkUpdateResponse;
import org.eclipse.kapua.service.elasticsearch.client.model.IndexRequest;
import org.eclipse.kapua.service.elasticsearch.client.model.IndexResponse;
import org.eclipse.kapua.service.elasticsearch.client.model.InsertRequest;
import org.eclipse.kapua.service.elasticsearch.client.model.InsertResponse;
import org.eclipse.kapua.service.elasticsearch.client.model.ResultList;
import org.eclipse.kapua.service.elasticsearch.client.model.TypeDescriptor;
import org.eclipse.kapua.service.elasticsearch.client.model.UpdateRequest;
import org.eclipse.kapua.service.elasticsearch.client.model.UpdateResponse;
import org.eclipse.kapua.service.elasticsearch.client.rest.exception.RequestEntityWriteError;
import org.eclipse.kapua.service.elasticsearch.client.rest.exception.ResponseEntityReadError;
import org.eclipse.kapua.service.elasticsearch.client.rest.utils.ApplicationJsonEntityBuilder;
import org.eclipse.kapua.service.elasticsearch.client.rest.utils.ContentTypeApplicationJsonHeader;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/**
 * Client implementation based on Elasticsearch rest client.
 * <p>
 * The Elasticsearch client provider is instantiated as singleton.
 *
 * @since 1.0.0
 */
public class RestElasticsearchClient extends AbstractElasticsearchClient<RestClient> {

    private static final Logger LOG = LoggerFactory.getLogger(RestElasticsearchClient.class);

    private static final Random RANDOM = RandomUtils.getInstance();
    private static final String MSG_EMPTY_ERROR = "Empty error message";

    private final ObjectMapper objectMapper;
    private static final String CLIENT_HITS_MAX_VALUE_EXCEEDED = "Total hits exceeds integer max value";
    private static final String QUERY_CONVERTED_QUERY = "Query - converted query: '{}'";
    private static final String COUNT_CONVERTED_QUERY = "Count - converted query: '{}'";

    private Counter restCallRuntimeExecCount;
    private Counter timeoutRetryCount;
    private Counter timeoutRetryLimitReachedCount;

    /**
     * Constructor.
     *
     * @since 1.0.0
     */
    public RestElasticsearchClient() {
        super("rest");

        objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public void init() throws ClientInitializationException {
        if (getClientConfiguration() == null) {
            throw new ClientInitializationException("Client configuration not defined");
        }
        if (getModelContext() == null) {
            throw new ClientInitializationException("Missing model context");
        }
        if (getModelConverter() == null) {
            throw new ClientInitializationException("Missing model converter");
        }

        MetricsService metricService = MetricServiceFactory.getInstance();
        restCallRuntimeExecCount = metricService.getCounter(getClientConfiguration().getModuleName(), DatastoreRestClientMetrics.METRIC_COMPONENT_NAME, DatastoreRestClientMetrics.METRIC_RUNTIME_EXEC, DatastoreRestClientMetrics.METRIC_COUNT);
        timeoutRetryCount = metricService.getCounter(getClientConfiguration().getModuleName(), DatastoreRestClientMetrics.METRIC_COMPONENT_NAME, DatastoreRestClientMetrics.METRIC_TIMEOUT_RETRY, DatastoreRestClientMetrics.METRIC_COUNT);
        timeoutRetryLimitReachedCount = metricService.getCounter(getClientConfiguration().getModuleName(), DatastoreRestClientMetrics.METRIC_COMPONENT_NAME, DatastoreRestClientMetrics.TIMEOUT_RETRY_LIMIT_REACHED, DatastoreRestClientMetrics.METRIC_COUNT);
    }

    @Override
    public void close() {
        // No resources to close
    }

    @Override
    public InsertResponse insert(InsertRequest insertRequest) throws ClientException {
        Map<String, Object> insertRequestStorableMap = getModelContext().marshal(insertRequest.getStorable());
        LOG.debug("Insert - converted object: '{}'", insertRequestStorableMap);

        String json = writeRequestFromMap(insertRequestStorableMap);

        Response insertResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_POST,
                                        getInsertTypePath(insertRequest),
                                        Collections.emptyMap(),
                                        ApplicationJsonEntityBuilder.buildFrom(json),
                                        new ContentTypeApplicationJsonHeader()
                                ),
                insertRequest.getTypeDescriptor().getIndex(),
                "INSERT");

        if (isRequestSuccessful(insertResponse)) {
            JsonNode responseNode = readResponseAsJsonNode(insertResponse);

            String id = responseNode.get(ElasticsearchKeywords.KEY_DOC_ID).asText();
            String index = responseNode.get(ElasticsearchKeywords.KEY_DOC_INDEX).asText();
            String type = responseNode.get(ElasticsearchKeywords.KEY_DOC_TYPE).asText();
            return new InsertResponse(id, new TypeDescriptor(index, type));
        } else {
            throw buildExceptionFromUnsuccessfulResponse("Insert", insertResponse);
        }
    }

    @Override
    public UpdateResponse upsert(UpdateRequest updateRequest) throws ClientException {
        Map<String, Object> updateRequestStorableMap = getModelContext().marshal(updateRequest.getStorable());

        Map<String, Object> updateRequestMap = new HashMap<>();
        updateRequestMap.put(ElasticsearchKeywords.KEY_DOC, updateRequestStorableMap);
        updateRequestMap.put(ElasticsearchKeywords.KEY_DOC_AS_UPSERT, true);
        LOG.debug("Upsert - converted object: '{}'", updateRequestMap);

        String json = writeRequestFromMap(updateRequestMap);

        Response updateResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_POST,
                                        getUpsertPath(updateRequest.getTypeDescriptor(), updateRequest.getId()),
                                        Collections.emptyMap(),
                                        ApplicationJsonEntityBuilder.buildFrom(json),
                                        new ContentTypeApplicationJsonHeader()),
                updateRequest.getTypeDescriptor().getIndex(),
                "UPSERT");

        if (isRequestSuccessful(updateResponse)) {
            JsonNode responseNode = readResponseAsJsonNode(updateResponse);

            String id = responseNode.get(ElasticsearchKeywords.KEY_DOC_ID).asText();
            String index = responseNode.get(ElasticsearchKeywords.KEY_DOC_INDEX).asText();
            String type = responseNode.get(ElasticsearchKeywords.KEY_DOC_TYPE).asText();
            return new UpdateResponse(id, new TypeDescriptor(index, type));
        } else {
            throw buildExceptionFromUnsuccessfulResponse("Update", updateResponse);
        }
    }

    @Override
    public BulkUpdateResponse upsert(BulkUpdateRequest bulkUpdateRequest) throws ClientException {
        StringBuilder bulkOperation = new StringBuilder();

        for (UpdateRequest upsertRequest : bulkUpdateRequest.getRequest()) {
            Map<String, Object> storableMap = getModelContext().marshal(upsertRequest.getStorable());

            bulkOperation.append("{ \"update\": {\"_id\": \"")
                    .append(upsertRequest.getId())
                    .append("\", \"_type\": \"")
                    .append(upsertRequest.getTypeDescriptor().getType())
                    .append("\", \"_index\": \"")
                    .append(upsertRequest.getTypeDescriptor().getIndex())
                    .append("\"}\n");

            bulkOperation.append("{ \"doc\": ");
            bulkOperation.append(writeRequestFromMap(storableMap));
            bulkOperation.append(", \"doc_as_upsert\": true }\n");
        }

        Response updateResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_POST,
                                        getBulkPath(),
                                        Collections.emptyMap(),
                                        ApplicationJsonEntityBuilder.buildFrom(bulkOperation.toString()),
                                        new ContentTypeApplicationJsonHeader()),
                "multi-index",
                "UPSERT BULK");

        if (isRequestSuccessful(updateResponse)) {
            JsonNode responseNode = readResponseAsJsonNode(updateResponse);

            ArrayNode items = (ArrayNode) responseNode.get(ElasticsearchKeywords.KEY_ITEMS);
            BulkUpdateResponse bulkResponse = new BulkUpdateResponse();
            for (JsonNode item : items) {
                JsonNode jsonNode = item.get(ElasticsearchKeywords.KEY_UPDATE);
                if (jsonNode != null) {
                    JsonNode idNode = jsonNode.get(ElasticsearchKeywords.KEY_DOC_ID);
                    String metricId = null;
                    if (idNode != null) {
                        metricId = idNode.asText();
                    }

                    String indexName = jsonNode.get(ElasticsearchKeywords.KEY_DOC_INDEX).asText();
                    String typeName = jsonNode.get(ElasticsearchKeywords.KEY_DOC_TYPE).asText();
                    int responseCode = jsonNode.get(ElasticsearchKeywords.KEY_STATUS).asInt();
                    if (!isRequestSuccessful(responseCode)) {
                        JsonNode failureNode = jsonNode.get(ElasticsearchKeywords.KEY_RESULT);
                        String failureMessage = MSG_EMPTY_ERROR;
                        if (failureNode != null) {
                            failureMessage = failureNode.asText();
                        }
                        bulkResponse.add(new UpdateResponse(metricId, new TypeDescriptor(indexName, typeName), failureMessage));
                        LOG.info("Upsert failed [{}, {}, {}]", indexName, typeName, failureMessage);
                        continue;
                    }
                    bulkResponse.add(new UpdateResponse(metricId, new TypeDescriptor(indexName, typeName)));
                    LOG.debug("Upsert on channel metric successfully executed [{}.{}, {}]", indexName, typeName, metricId);
                } else {
                    throw new ClientInternalError("Empty JSON response from upsert");
                }
            }
            return bulkResponse;
        } else {
            throw buildExceptionFromUnsuccessfulResponse("Upsert", updateResponse);
        }
    }

    @Override
    public <T> T find(TypeDescriptor typeDescriptor, Object query, Class<T> clazz) throws ClientException {
        ResultList<T> result = query(typeDescriptor, query, clazz);

        return result.getResult().isEmpty() ? null : result.getResult().get(0);
    }

    @Override
    public <T> ResultList<T> query(TypeDescriptor typeDescriptor, Object query, Class<T> clazz) throws ClientException {
        JsonNode queryJsonNode = getModelConverter().convertQuery(query);
        Object queryFetchStyle = getModelConverter().getFetchStyle(query);
        LOG.debug(QUERY_CONVERTED_QUERY, queryJsonNode);

        String json = writeRequestFromJsonNode(queryJsonNode);

        long totalCount = 0;
        ArrayNode resultsNode = null;
        Response queryResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_GET,
                                        getSearchPath(typeDescriptor),
                                        Collections.emptyMap(),
                                        ApplicationJsonEntityBuilder.buildFrom(json),
                                        new ContentTypeApplicationJsonHeader()),
                typeDescriptor.getIndex(), "QUERY");

        if (isRequestSuccessful(queryResponse)) {
            JsonNode responseNode = readResponseAsJsonNode(queryResponse);

            JsonNode hitsNode = responseNode.get(ElasticsearchKeywords.KEY_HITS);
            totalCount = hitsNode.get(ElasticsearchKeywords.KEY_TOTAL).asLong();
            if (totalCount > Integer.MAX_VALUE) {
                throw new ClientException(ClientErrorCodes.ACTION_ERROR, CLIENT_HITS_MAX_VALUE_EXCEEDED);
            }
            resultsNode = ((ArrayNode) hitsNode.get(ElasticsearchKeywords.KEY_HITS));
        } else if (queryResponse != null) {
            throw buildExceptionFromUnsuccessfulResponse("Query", queryResponse);
        }

        ResultList<T> resultList = new ResultList<>(totalCount);
        if (resultsNode != null && resultsNode.size() > 0) {
            for (JsonNode result : resultsNode) {
                Map<String, Object> object = objectMapper.convertValue(result.get(SchemaKeys.KEY_SOURCE), Map.class);

                String id = result.get(ElasticsearchKeywords.KEY_DOC_ID).asText();
                String index = result.get(ElasticsearchKeywords.KEY_DOC_INDEX).asText();
                String type = result.get(ElasticsearchKeywords.KEY_DOC_TYPE).asText();

                object.put(ModelContext.TYPE_DESCRIPTOR_KEY, new TypeDescriptor(index, type));
                object.put(getModelContext().getIdKeyName(), id);
                object.put(QueryConverter.QUERY_FETCH_STYLE_KEY, queryFetchStyle);

                resultList.add(getModelContext().unmarshal(clazz, object));
            }
        }
        return resultList;
    }

    @Override
    public long count(TypeDescriptor typeDescriptor, Object query) throws ClientException {
        JsonNode queryJsonNode = getModelConverter().convertQuery(query);
        LOG.debug(COUNT_CONVERTED_QUERY, queryJsonNode);

        String json = writeRequestFromJsonNode(queryJsonNode);

        Response queryResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_GET,
                                        getSearchPath(typeDescriptor),
                                        Collections.emptyMap(),
                                        ApplicationJsonEntityBuilder.buildFrom(json),
                                        new ContentTypeApplicationJsonHeader()),
                typeDescriptor.getIndex(), "COUNT");

        long totalCount = 0;
        if (isRequestSuccessful(queryResponse)) {
            JsonNode responseNode = readResponseAsJsonNode(queryResponse);

            JsonNode hitsNode = responseNode.get(ElasticsearchKeywords.KEY_HITS);
            totalCount = hitsNode.get(ElasticsearchKeywords.KEY_TOTAL).asLong();
            if (totalCount > Integer.MAX_VALUE) {
                throw new ClientException(ClientErrorCodes.ACTION_ERROR, CLIENT_HITS_MAX_VALUE_EXCEEDED);
            }
        } else if (queryResponse != null) {
            throw buildExceptionFromUnsuccessfulResponse("Count", queryResponse);
        }

        return totalCount;
    }

    @Override
    public void delete(TypeDescriptor typeDescriptor, String id) throws ClientException {
        LOG.debug("Delete - id: '{}'", id);
        Response deleteResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_DELETE,
                                        getIdPath(typeDescriptor, id),
                                        Collections.emptyMap()
                                ),
                typeDescriptor.getIndex(),
                ElasticsearchKeywords.ACTION_DELETE);

        if (deleteResponse != null && !isRequestSuccessful(deleteResponse)) {
            throw buildExceptionFromUnsuccessfulResponse("Delete", deleteResponse);
        }
    }

    @Override
    public void deleteByQuery(TypeDescriptor typeDescriptor, Object query) throws ClientException {
        JsonNode queryJsonNode = getModelConverter().convertQuery(query);
        LOG.debug(QUERY_CONVERTED_QUERY, queryJsonNode);

        String json = writeRequestFromJsonNode(queryJsonNode);

        Response deleteResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_POST,
                                        getDeleteByQueryPath(typeDescriptor),
                                        Collections.emptyMap(),
                                        ApplicationJsonEntityBuilder.buildFrom(json),
                                        new ContentTypeApplicationJsonHeader()),
                typeDescriptor.getIndex(),
                "DELETE BY QUERY");

        if (deleteResponse != null && !isRequestSuccessful(deleteResponse)) {
            throw buildExceptionFromUnsuccessfulResponse("Delete by query", deleteResponse);
        }
    }

    @Override
    public IndexResponse isIndexExists(IndexRequest indexRequest) throws ClientException {
        LOG.debug("Index exists - index name: '{}'", indexRequest.getIndex());
        Response isIndexExistsResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_HEAD,
                                        getIndexPath(indexRequest.getIndex()),
                                        Collections.emptyMap()
                                ),
                indexRequest.getIndex(),
                "INDEX EXIST");

        if (isIndexExistsResponse != null && isIndexExistsResponse.getStatusLine() != null) {
            if (isIndexExistsResponse.getStatusLine().getStatusCode() == 200) {
                return new IndexResponse(true);
            } else if (isIndexExistsResponse.getStatusLine().getStatusCode() == 404) {
                return new IndexResponse(false);
            }
        }

        throw buildExceptionFromUnsuccessfulResponse("Index exists", isIndexExistsResponse);
    }

    @Override
    public IndexResponse findIndexes(IndexRequest indexRequest) throws ClientException {
        LOG.debug("Find indexes - index prefix: '{}'", indexRequest.getIndex());
        Response isIndexExistsResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_GET,
                                        getFindIndexPath(indexRequest.getIndex()),
                                        Collections.singletonMap("pretty", "true")
                                ),
                indexRequest.getIndex(),
                "INDEX EXIST");

        if (isIndexExistsResponse != null && isIndexExistsResponse.getStatusLine() != null) {
            if (isIndexExistsResponse.getStatusLine().getStatusCode() == 200) {
                try {
                    return new IndexResponse(EntityUtils.toString(isIndexExistsResponse.getEntity()).split("\n"));
                } catch (ParseException | IOException e) {
                    throw new ClientInternalError(e, "Cannot convert the indexes list");
                }
            } else if (isIndexExistsResponse.getStatusLine().getStatusCode() == 404) {
                return new IndexResponse(null);
            }
        }

        throw buildExceptionFromUnsuccessfulResponse("Find indexes", isIndexExistsResponse);
    }

    @Override
    public void createIndex(String indexName, ObjectNode indexSettings) throws ClientException {
        LOG.debug("Create index - object: '{}'", indexSettings);

        String json = writeRequestFromJsonNode(indexSettings);

        Response createIndexResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_PUT,
                                        getIndexPath(indexName),
                                        Collections.emptyMap(),
                                        ApplicationJsonEntityBuilder.buildFrom(json),
                                        new ContentTypeApplicationJsonHeader()),
                indexName,
                "CREATE INDEX");

        if (!isRequestSuccessful(createIndexResponse)) {
            throw buildExceptionFromUnsuccessfulResponse("Create index", createIndexResponse);
        }
    }

    @Override
    public boolean isMappingExists(TypeDescriptor typeDescriptor) throws ClientException {
        LOG.debug("Mapping exists - mapping name: '{} - {}'", typeDescriptor.getIndex(), typeDescriptor.getType());
        Response isMappingExistsResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_GET,
                                        getMappingPath(typeDescriptor),
                                        Collections.emptyMap()
                                ),
                typeDescriptor.getIndex(),
                "MAPPING EXIST");

        if (isMappingExistsResponse != null && isMappingExistsResponse.getStatusLine() != null) {
            if (isMappingExistsResponse.getStatusLine().getStatusCode() == 200) {
                return true;
            } else if (isMappingExistsResponse.getStatusLine().getStatusCode() == 404) {
                return false;
            }
        }

        throw buildExceptionFromUnsuccessfulResponse("Mapping exists", isMappingExistsResponse);
    }

    @Override
    public void putMapping(TypeDescriptor typeDescriptor, JsonNode mapping) throws ClientException {
        LOG.debug("Create mapping - object: '{}, index: {}, type: {}'", mapping, typeDescriptor.getIndex(), typeDescriptor.getType());

        String json = writeRequestFromJsonNode(mapping);

        Response createMappingResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_PUT,
                                        getMappingPath(typeDescriptor),
                                        Collections.emptyMap(),
                                        ApplicationJsonEntityBuilder.buildFrom(json),
                                        new ContentTypeApplicationJsonHeader()),
                typeDescriptor.getIndex(),
                "PUT MAPPING");

        if (!isRequestSuccessful(createMappingResponse)) {
            throw buildExceptionFromUnsuccessfulResponse("Create mapping", createMappingResponse);
        }
    }

    @Override
    public void refreshAllIndexes() throws ClientException {
        LOG.debug("Refresh all indexes");
        Response refreshIndexResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_POST,
                                        getRefreshAllIndexesPath(),
                                        Collections.emptyMap()
                                ),
                ElasticsearchKeywords.INDEX_ALL,
                "REFRESH INDEX");

        if (!isRequestSuccessful(refreshIndexResponse)) {
            throw buildExceptionFromUnsuccessfulResponse("Refresh all indexes", refreshIndexResponse);
        }
    }

    @Override
    public void deleteAllIndexes() throws ClientException {
        LOG.debug("Delete all indexes");
        Response deleteIndexResponse = restCallTimeoutHandler(() ->
                        getClient()
                                .performRequest(
                                        ElasticsearchKeywords.ACTION_DELETE,
                                        getIndexPath("_all"),
                                        Collections.emptyMap()
                                ),
                ElasticsearchKeywords.INDEX_ALL,
                "DELETE INDEX");

        if (!isRequestSuccessful(deleteIndexResponse)) {
            throw buildExceptionFromUnsuccessfulResponse("Delete all indexes", deleteIndexResponse);
        }
    }

    @Override
    public void deleteIndexes(String... indexes) throws ClientException {
        LOG.debug("Delete indexes");
        for (String index : indexes) {
            LOG.debug("Delete index: {}", index);
            Response deleteIndexResponse = restCallTimeoutHandler(() -> {
                LOG.debug("Deleting index: {}", index);

                return getClient()
                        .performRequest(
                                ElasticsearchKeywords.ACTION_DELETE,
                                getIndexPath(index),
                                Collections.emptyMap()
                        );
            }, index, "DELETE INDEX");

            // for that call the deleteIndexResponse=null case could be considered as good response since if an index doesn't exist (404) the delete could be considered successful.
            // the deleteIndexResponse is null also if the error is due to a bad index request (400) but this error, except if there is an application bug, shouldn't never happen.
            if (deleteIndexResponse == null) {
                LOG.debug("Deleting index: {} - index does not exist", index);
            } else if (!isRequestSuccessful(deleteIndexResponse)) {
                throw buildExceptionFromUnsuccessfulResponse("Delete indexes", deleteIndexResponse);
            }
            LOG.debug("Deleting index: {} - index deleted", index);
        }
    }

    private <T> T restCallTimeoutHandler(Callable<T> restAction, String index, String operationName) throws ClientException {
        int retryCount = 0;
        try {

            do {
                try {
                    return restAction.call();
                } catch (TimeoutException te) {
                    timeoutRetryCount.inc();
                    if (retryCount < getClientConfiguration().getRequestConfiguration().getRequestRetryAttemptMax() - 1) {
                        try {
                            Thread.sleep((long) (getClientConfiguration().getRequestConfiguration().getRequestRetryAttemptWait() * (0.5 + RANDOM.nextFloat() / 2)));
                        } catch (InterruptedException e1) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (RuntimeException e) {
                    restCallRuntimeExecCount.inc();
                    throw e;
                }
            } while (++retryCount <= getClientConfiguration().getRequestConfiguration().getRequestRetryAttemptMax());

        } catch (ResponseException re) {
            if (re.getResponse().getStatusLine().getStatusCode() == 404) {
                LOG.warn("Resource for index '{}' not found on action '{}'! {}", index, operationName, re.getLocalizedMessage());
                return null;
            } else if (re.getResponse().getStatusLine().getStatusCode() == 400) {
                LOG.warn("Bad request for index '{}' on action '{}'! {}", index, operationName, re.getLocalizedMessage());
                return null;
            } else {
                throw new ClientException(ClientErrorCodes.ACTION_ERROR, re);
            }
        } catch (Exception e) {
            throw new ClientInternalError(e, "Error in handling REST timeout handler");
        }
        timeoutRetryLimitReachedCount.inc();

        throw new ClientCommunicationException();
    }

    /**
     * Checks if the given {@link Response#getStatusLine} as a HTTP 2xx code.
     *
     * @param response The {@link Response} to check.
     * @return {@code true} if {@link Response#getStatusLine()} has a 2xx HTTP code, {@code false} otherwise.
     * @since 1.0.0
     */
    private boolean isRequestSuccessful(Response response) {
        if (response != null && response.getStatusLine() != null) {
            return isRequestSuccessful(response.getStatusLine().getStatusCode());
        } else {
            return false;
        }
    }

    /**
     * Checks if the given response code is a HTTP 2xx code.
     *
     * @param responseCode The response code to check.
     * @return {@code true} if reponse code is a 2xx HTTP code, {@code false} otherwise.
     * @since 1.0.0
     */
    private boolean isRequestSuccessful(int responseCode) {
        return (200 <= responseCode && responseCode <= 299);
    }

    /**
     * Builds a {@link ClientActionResponseException} from the {@link Response} trying to get the reason from it.
     *
     * @param action   The action that was performed
     * @param response The {@link Response} from Elasticsearch
     * @return The {@link ClientActionResponseException} to throw.
     * @since 1.3.0
     */
    private ClientException buildExceptionFromUnsuccessfulResponse(String action, Response response) {
        String reason;
        if (response != null && response.getStatusLine() != null) {
            reason = response.getStatusLine().getReasonPhrase();
        } else {
            reason = "Unknown. Cannot get the reason from Response";
        }

        String responseCodeString;
        if (response != null && response.getStatusLine() != null) {
            responseCodeString = String.valueOf(response.getStatusLine().getStatusCode());
        } else {
            responseCodeString = "Unknown";
        }

        return new ClientActionResponseException(action, reason, responseCodeString);
    }

    private JsonNode readResponseAsJsonNode(Response response) throws ResponseEntityReadError {
        try {
            return objectMapper.readTree(EntityUtils.toString(response.getEntity()));
        } catch (IOException e) {
            throw new ResponseEntityReadError(e);
        }
    }

    private String writeRequestFromJsonNode(JsonNode jsonNode) throws RequestEntityWriteError {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new RequestEntityWriteError(e);
        }
    }

    private String writeRequestFromMap(Map<String, Object> storableMap) throws RequestEntityWriteError {
        try {
            return objectMapper.writeValueAsString(storableMap);
        } catch (JsonProcessingException e) {
            throw new RequestEntityWriteError(e);
        }
    }

    private String getRefreshAllIndexesPath() {
        return "/_all/_refresh";
    }

    private String getIndexPath(String index) {
        return String.format("/%s", index);
    }

    private String getFindIndexPath(String index) {
        return String.format("/_cat/indices?h=index&index=%s", index);
    }

    private String getBulkPath() {
        return "/_bulk";
    }

    private String getInsertTypePath(InsertRequest request) {
        if (request.getId() != null) {
            return String.format("%s?id=%s&version=1&version_type=external", getTypePath(request.getTypeDescriptor()), request.getId());
        } else {
            return getTypePath(request.getTypeDescriptor());
        }
    }

    private String getTypePath(TypeDescriptor typeDescriptor) {
        return String.format("/%s/%s", typeDescriptor.getIndex(), typeDescriptor.getType());
    }

    private String getDeleteByQueryPath(TypeDescriptor typeDescriptor) {
        return String.format("/%s/%s/_delete_by_query", typeDescriptor.getIndex(), typeDescriptor.getType());
    }

    private String getMappingPath(TypeDescriptor typeDescriptor) {
        return String.format("/%s/_mapping/%s", typeDescriptor.getIndex(), typeDescriptor.getType());
    }

    private String getIdPath(TypeDescriptor typeDescriptor, String id) throws UnsupportedEncodingException {
        return String.format("/%s/%s/%s", typeDescriptor.getIndex(), typeDescriptor.getType(), URLEncoder.encode(id, "UTF-8"));
    }

    private String getUpsertPath(TypeDescriptor typeDescriptor, String id) throws UnsupportedEncodingException {
        return String.format("/%s/%s/%s/_update", typeDescriptor.getIndex(), typeDescriptor.getType(), URLEncoder.encode(id, "UTF-8"));
    }

    private String getSearchPath(TypeDescriptor typeDescriptor) {
        return String.format("/%s/%s/_search", typeDescriptor.getIndex(), typeDescriptor.getType());
    }

}
