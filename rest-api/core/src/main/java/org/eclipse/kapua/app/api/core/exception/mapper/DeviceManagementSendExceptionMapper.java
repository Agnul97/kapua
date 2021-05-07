/*******************************************************************************
 * Copyright (c) 2021 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.app.api.core.exception.mapper;

import org.eclipse.kapua.app.api.core.exception.model.DeviceManagementSendExceptionInfo;
import org.eclipse.kapua.service.device.management.exception.DeviceManagementSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class DeviceManagementSendExceptionMapper implements ExceptionMapper<DeviceManagementSendException> {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceManagementSendExceptionMapper.class);

    @Override
    public Response toResponse(DeviceManagementSendException deviceManagementSendException) {
        LOG.error("Device Management Send", deviceManagementSendException);
        return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .entity(new DeviceManagementSendExceptionInfo(deviceManagementSendException))
                .build();
    }
}
