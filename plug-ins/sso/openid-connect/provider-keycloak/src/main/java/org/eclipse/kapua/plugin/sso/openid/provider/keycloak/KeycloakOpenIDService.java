/*******************************************************************************
 * Copyright (c) 2017, 2022 Red Hat Inc and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc - initial API and implementation
 *     Eurotech
 *******************************************************************************/
package org.eclipse.kapua.plugin.sso.openid.provider.keycloak;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.plugin.sso.openid.SSOData;
import org.eclipse.kapua.plugin.sso.openid.exception.OpenIDIllegalArgumentException;
import org.eclipse.kapua.plugin.sso.openid.provider.AbstractOpenIDService;
import org.eclipse.kapua.plugin.sso.openid.provider.setting.OpenIDSetting;
import org.eclipse.kapua.service.account.Account;
import org.eclipse.kapua.service.account.AccountService;

import java.math.BigInteger;

/**
 * The Keycloak OpenID service class.
 */
public class KeycloakOpenIDService extends AbstractOpenIDService {

    private static final String KEYCLOAK_AUTH_URI_SUFFIX = "/protocol/openid-connect/auth";
    private static final String KEYCLOAK_TOKEN_URI_SUFFIX = "/protocol/openid-connect/token";
    private static final String KEYCLOAK_USERINFO_URI_SUFFIX = "/protocol/openid-connect/userinfo";
    private static final String KEYCLOAK_LOGOUT_URI_SUFFIX = "/protocol/openid-connect/logout";

    private final AccountService accountService;
    private final KeycloakOpenIDUtils keycloakOpenIDUtils;
    private final KeycloakAdminClient keycloakAdminClient;

    public KeycloakOpenIDService(final OpenIDSetting ssoSettings,
                                 KeycloakOpenIDUtils keycloakOpenIDUtils,
                                 KeycloakAdminClient keycloakAdminClient,
                                 AccountService accountService) {
        super(ssoSettings);
        this.keycloakOpenIDUtils = keycloakOpenIDUtils;
        this.keycloakAdminClient = keycloakAdminClient;
        this.accountService = accountService;
    }

    @Override
    protected String getAuthUri() throws OpenIDIllegalArgumentException {
        return keycloakOpenIDUtils.getProviderUri() + keycloakOpenIDUtils.KEYCLOAK_URI_COMMON_PART +
                keycloakOpenIDUtils.getRealm() + KEYCLOAK_AUTH_URI_SUFFIX;
    }

    @Override
    protected String getLogoutUri() throws OpenIDIllegalArgumentException {
        return keycloakOpenIDUtils.getProviderUri() + keycloakOpenIDUtils.KEYCLOAK_URI_COMMON_PART +
                keycloakOpenIDUtils.getRealm() + KEYCLOAK_LOGOUT_URI_SUFFIX;
    }

    @Override
    protected String getTokenUri() throws OpenIDIllegalArgumentException {
        return keycloakOpenIDUtils.getProviderUri() + keycloakOpenIDUtils.KEYCLOAK_URI_COMMON_PART +
                keycloakOpenIDUtils.getRealm() + KEYCLOAK_TOKEN_URI_SUFFIX;
    }

    @Override
    protected String getUserInfoUri() throws OpenIDIllegalArgumentException {
        return keycloakOpenIDUtils.getProviderUri() + keycloakOpenIDUtils.KEYCLOAK_URI_COMMON_PART +
                keycloakOpenIDUtils.getRealm() + KEYCLOAK_USERINFO_URI_SUFFIX;
    }

    @Override
    public SSOData retrieveSSODataForThisAccount(Account account) throws KapuaException {
        SSODataKeycloak ssoData = new SSODataKeycloak(account);
        if (account.getId().equals(KapuaId.ONE)) { //root account
            ssoData.setAccountSupportsDirectLogin(keycloakAdminClient.findOrganizationByAccountId(account.getName()).isPresent());
        } else {
            String parentAccountPath = account.getParentAccountPath();
            String lv1AccountId = getLv1AccountId(parentAccountPath);
            Account lv1Account = KapuaSecurityUtils.doPrivileged(() -> accountService.find(new KapuaEid(new BigInteger(lv1AccountId))));
            ssoData.setAccountSupportsDirectLogin(keycloakAdminClient.findOrganizationByAccountId(lv1Account.getName()).isPresent());
        }
        return ssoData;

    }

    private static String getLv1AccountId(String parentAccountPath) {
        int firstSlash = parentAccountPath.indexOf('/');
        int secondSlash = parentAccountPath.indexOf('/', firstSlash + 1);
        int thirdSlash = parentAccountPath.indexOf('/', secondSlash + 1);
        String lv1AccountId;
        if (thirdSlash == -1) { //it's a level-1 account
            lv1AccountId = parentAccountPath.substring(secondSlash + 1);
        } else {
            lv1AccountId = parentAccountPath.substring(secondSlash + 1, thirdSlash);
        }
        return lv1AccountId;
    }


    @Override
    public String getId() {
        return "keycloak";
    }
}
