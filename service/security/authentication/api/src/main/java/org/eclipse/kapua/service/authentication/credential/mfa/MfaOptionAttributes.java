/*******************************************************************************
 * Copyright (c) 2020, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.service.authentication.credential.mfa;

import org.eclipse.kapua.model.KapuaUpdatableEntityAttributes;

/**
 * {@link MfaOption} {@link KapuaUpdatableEntityAttributes}.
 *
 * @since 1.3.0
 */
public class MfaOptionAttributes extends KapuaUpdatableEntityAttributes {

    /**
     * {@link MfaOption#getUserId()}
     *
     * @since 1.3.0
     */
    public static final String USER_ID = "userId";

    /**
     * {@link MfaOption#getMfaSecretKey()}
     *
     * @since 1.3.0
     */
    public static final String MFA_SECRET_KEY = "mfaSecretKey";

    /**
     * {@link MfaOption#getHasTrustMe()}
     *
     * @since 2.1.0
     */
    public static final String HAS_TRUST_ME = "hasTrustMe";

}
