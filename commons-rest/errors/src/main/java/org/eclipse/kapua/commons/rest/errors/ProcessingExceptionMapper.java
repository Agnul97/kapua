/*******************************************************************************
 * Copyright (c) 2017, 2022 Eurotech and/or its affiliates and others
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

import javax.inject.Inject;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.core.Response;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.MediaType;

@Provider
public class ProcessingExceptionMapper implements ExceptionMapper<ProcessingException> {

    private final boolean showStackTrace;

    @Inject
    public ProcessingExceptionMapper(ExceptionConfigurationProvider exceptionConfigurationProvider) {
        this.showStackTrace = exceptionConfigurationProvider.showStackTrace();
    }

    @Override
    public Response toResponse(ProcessingException ex) {
        Throwable cause = ex.getCause();

        // Handle known cause types
        if (cause instanceof IllegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid request: " + cause.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        // Optional: log or inspect the full chain
        ex.printStackTrace(); // Or use a proper logger

        // Fallback for other causes
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Server error during request processing.")
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
