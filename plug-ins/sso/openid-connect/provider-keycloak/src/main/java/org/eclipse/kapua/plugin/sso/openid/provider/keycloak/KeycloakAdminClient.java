/*******************************************************************************
 * Copyright (c) 2024, 2025 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.plugin.sso.openid.provider.keycloak;

import com.google.common.base.Strings;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.kapua.plugin.sso.openid.exception.OpenIDException;
import org.eclipse.kapua.plugin.sso.openid.exception.OpenIDTokenException;
import org.eclipse.kapua.plugin.sso.openid.provider.setting.OpenIDSetting;
import org.eclipse.kapua.plugin.sso.openid.provider.setting.OpenIDSettingKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

/**
 * HTTP client for the Keycloak Admin REST API.
 * <p>
 * Authenticates via {@code client_credentials} grant using the configured
 * {@code sso.openid.client.id} / {@code sso.openid.client.secret}.
 * The service account associated to the client must have the
 * {@code view-organizations} (or {@code realm-admin}) role assigned.
 * </p>
 *
 * @since 2.1.0
 */
public class KeycloakAdminClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private static final String TOKEN_PATH = "/realms/%s/protocol/openid-connect/token";
    private static final String ADMIN_ORGANIZATIONS_PATH = "/admin/realms/%s/organizations";

    private final KeycloakOpenIDUtils keycloakOpenIDUtils;
    private final OpenIDSetting openIDSetting;
    private final CloseableHttpClient httpClient;

    /** Cached access token — lazily obtained and refreshed on 401. */
    private String accessToken;

    /**
     * Constructor.
     *
     * @param keycloakOpenIDUtils provides the Keycloak URI and realm.
     * @param openIDSetting       provides the client id and client secret.
     * @since 2.1.0
     */
    @Inject
    public KeycloakAdminClient(KeycloakOpenIDUtils keycloakOpenIDUtils,
                               OpenIDSetting openIDSetting) {
        this.keycloakOpenIDUtils = keycloakOpenIDUtils;
        this.openIDSetting = openIDSetting;
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Searches for a Keycloak Organization whose attribute {@code "accountid"} matches the given value.
     * <p>
     * Uses: {@code GET /admin/realms/{realm}/organizations?q=accountid:{accountId}}
     * </p>
     * <p>
     * Requires Keycloak 24+ with the Organizations feature enabled.
     * </p>
     *
     * @param accountName the value of the {@code accountid} organization attribute to search for.
     * @return the first matching organization as a {@link JsonObject}, or {@link Optional#empty()} if not found.
     * @throws OpenIDException if authentication or the HTTP call fails.
     * @since 2.1.0
     */
    public Optional<JsonObject> findOrganizationByAccountId(String accountName) throws OpenIDException {
        if (Strings.isNullOrEmpty(accountName)) {
            return Optional.empty();
        }
        if (accessToken == null) {
            authenticate();
        }
        return doFindOrganizationByAccountId(accountName, false);
    }

    // -- Private helpers ------------------------------------------------------

    /**
     * Performs the actual search call, with a single re-auth retry on 401.
     *
     * @since 2.1.0
     */
    private Optional<JsonObject> doFindOrganizationByAccountId(String accountName, boolean isRetry) throws OpenIDException {
        try {
            String url = keycloakOpenIDUtils.getProviderUri()
                    + String.format(ADMIN_ORGANIZATIONS_PATH, keycloakOpenIDUtils.getRealm())
                    + "?q=accountid:" + accountName; //TODO: this needs to be not hardcoded

            LOG.debug("GET {}", url);
            HttpGet get = new HttpGet(url);
            get.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            get.setHeader(HttpHeaders.ACCEPT, "application/json");

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity());
                LOG.debug("GET {} - Response: {} - Body: {}", url, status, body);

                if (status == HttpStatus.SC_UNAUTHORIZED && !isRetry) {
                    // Token expired — re-authenticate once and retry
                    LOG.debug("Access token expired, re-authenticating...");
                    authenticate();
                    return doFindOrganizationByAccountId(accountName, true);
                }
                if (status != HttpStatus.SC_OK) {
                    throw new OpenIDTokenException(new IOException(
                            "Keycloak Admin API returned HTTP " + status + " for organizations search: " + body));
                }

                try (JsonReader reader = Json.createReader(new StringReader(body))) {
                    JsonArray results = reader.readArray();
                    if (!results.isEmpty()) {
                        return Optional.of(results.getJsonObject(0));
                    }
                }
                return Optional.empty();
            }
        } catch (OpenIDException oe) {
            throw oe;
        } catch (Exception e) {
            throw new OpenIDTokenException(e);
        }
    }

    /**
     * Obtains a {@code client_credentials} access token from Keycloak and caches it.
     *
     * @throws OpenIDTokenException if the token request fails.
     * @since 2.1.0
     */
    private void authenticate() throws OpenIDException {
        try {
            String tokenUrl = keycloakOpenIDUtils.getProviderUri()
                    + String.format(TOKEN_PATH, keycloakOpenIDUtils.getRealm());

            String clientId = openIDSetting.getString(OpenIDSettingKeys.SSO_OPENID_CLIENT_ID);
            String clientSecret = openIDSetting.getString(OpenIDSettingKeys.SSO_OPENID_CLIENT_SECRET);

            LOG.debug("Authenticating to Keycloak Admin API at {}", tokenUrl);
            HttpPost post = new HttpPost(tokenUrl);
            post.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
            post.setEntity(new StringEntity(
                    "grant_type=client_credentials"
                    + "&client_id=" + clientId
                    + "&client_secret=" + clientSecret,
                    ContentType.APPLICATION_FORM_URLENCODED
            ));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity());

                if (status != HttpStatus.SC_OK) {
                    throw new OpenIDTokenException(new IOException(
                            "Keycloak token endpoint returned HTTP " + status + ": " + body));
                }

                try (JsonReader reader = Json.createReader(new StringReader(body))) {
                    this.accessToken = reader.readObject().getString("access_token");
                }
                LOG.debug("Successfully authenticated to Keycloak Admin API.");
            }
        } catch (OpenIDException oe) {
            throw oe;
        } catch (Exception e) {
            throw new OpenIDTokenException(e);
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}

