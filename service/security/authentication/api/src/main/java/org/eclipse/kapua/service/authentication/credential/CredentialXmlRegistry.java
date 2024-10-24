/*******************************************************************************
 * Copyright (c) 2016, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.service.authentication.credential;

import org.eclipse.kapua.locator.KapuaLocator;

import javax.xml.bind.annotation.XmlRegistry;

/**
 * The {@link Credential} {@link XmlRegistry}
 *
 * @since 1.0.0
 */
@XmlRegistry
public class CredentialXmlRegistry {

    private final CredentialFactory credentialFactory = KapuaLocator.getInstance().getFactory(CredentialFactory.class);

    /**
     * Instantiates a new {@link Credential}.
     *
     * @return The newly instantiated {@link Credential}
     * @since 1.0.0
     */
    public Credential newCredential() {
        return credentialFactory.newEntity(null);
    }

    /**
     * Instantiates a new {@link CredentialCreator}.
     *
     * @return The newly instantiated {@link CredentialCreator}
     * @since 1.0.0
     */
    public CredentialCreator newCredentialCreator() {
        return credentialFactory.newCreator(null, null, null, null, null, null);
    }

    /**
     * Instantiates a new {@link CredentialListResult}.
     *
     * @return The newly instantiated {@link CredentialListResult}
     * @since 1.0.0
     */
    public CredentialListResult newCredentialListResult() {
        return credentialFactory.newListResult();
    }

    /**
     * Instantiates a new {@link CredentialQuery}.
     *
     * @return The newly instantiated {@link CredentialQuery}
     * @since 1.0.0
     */
    public CredentialQuery newCredentialQuery() {
        return credentialFactory.newQuery(null);
    }
}
