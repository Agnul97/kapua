/*******************************************************************************
 * Copyright (c) 2018, 2022 Eurotech and/or its affiliates and others
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

import org.eclipse.kapua.plugin.sso.openid.SSOData;
import org.eclipse.kapua.service.account.Account;

/**
 * Keycloak-specific implementation of {@link SSOData}.
 *
 * @since 2.0.0
 */
public class SSODataKeycloak implements SSOData {

    private Account account;
    private boolean supportsDirectLogin;

    /**
     * No-arg constructor required for JAXB deserialization.
     *
     * @since 2.0.0
     */
    public SSODataKeycloak() {
    }

    public SSODataKeycloak(Account account) {
        this.account = account;
    }

    @Override
    public void setAccount(Account account) {
        this.account = account;
    }

    @Override
    public Account getAccount() {
        return account;
    }

    @Override
    public void setAccountSupportsDirectLogin(boolean supportDirectLogin) {
        this.supportsDirectLogin = supportDirectLogin;
    }

    @Override
    public boolean getAccountSupportsDirectLogin() {
        return supportsDirectLogin;
    }

    @Override
    public void setUriSuffixDirectLogin(String suffix) {
        // no-op: suffix is calculated from account name
    }

    @Override
    public String getUriSuffixDirectLogin() {
        return account != null ? "?accountid=" + account.getName() : null;
    }
}
