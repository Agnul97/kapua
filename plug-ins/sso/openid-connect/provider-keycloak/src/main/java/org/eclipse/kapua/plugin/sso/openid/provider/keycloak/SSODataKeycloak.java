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

import org.eclipse.kapua.service.account.Account;

public class SSODataKeycloak implements org.eclipse.kapua.plugin.sso.openid.SSOData {

    Account account;
    boolean supportsDirectLogin;

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

    public boolean getAccountSupportsDirectLogin() {
        return supportsDirectLogin;
    }

    @Override
    public void setUriSuffixDirectLogin(String suffix) {
        //no-op since we calculate suffix in this pojo
    }

    public String getUriSuffixDirectLogin() {
        return "?accountid=" + account.getName();
    }

}
