/*******************************************************************************
 * Copyright (c) 2024, 2026 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.commons.rest.errors;

import org.eclipse.kapua.commons.rest.model.errors.ThrowableInfo;
import org.eclipse.kapua.plugin.sso.openid.exception.OpenIDException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * {@link ExceptionMapper} for {@link OpenIDException}.
 *
 * @since 2.1.0
 */
@Provider
public class OpenIDExceptionMapper implements ExceptionMapper<OpenIDException> {

    private static final Logger LOG = LoggerFactory.getLogger(OpenIDExceptionMapper.class);

    private final boolean showStackTrace;

    @Inject
    public OpenIDExceptionMapper(ExceptionConfigurationProvider exceptionConfigurationProvider) {
        this.showStackTrace = exceptionConfigurationProvider.showStackTrace();
    }

    @Override
    public Response toResponse(OpenIDException openIDException) {
        LOG.error(openIDException.getMessage(), openIDException);
        Throwable cause = openIDException.getCause();
        Throwable messageSource = (cause != null) ? cause : openIDException;

        return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .entity(new ThrowableInfo(Status.INTERNAL_SERVER_ERROR.getStatusCode(), messageSource, showStackTrace))
                .build();
    }
}

