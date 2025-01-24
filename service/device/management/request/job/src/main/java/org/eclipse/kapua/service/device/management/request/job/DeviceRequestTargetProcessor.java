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
package org.eclipse.kapua.service.device.management.request.job;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.job.engine.commons.operation.AbstractDeviceTargetProcessor;
import org.eclipse.kapua.job.engine.commons.wrappers.JobTargetWrapper;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.device.management.request.DeviceRequestManagementService;
import org.eclipse.kapua.service.device.management.request.message.request.GenericRequestMessage;
import org.eclipse.kapua.service.device.management.request.job.definition.DeviceRequestPropertyKeys;
import org.eclipse.kapua.service.job.operation.TargetProcessor;
import org.eclipse.kapua.service.job.targets.JobTarget;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.inject.Inject;

/**
 * {@link TargetProcessor} for {@link DeviceRequestManagementService#exec(KapuaId, KapuaId, GenericRequestMessage, Long)}.
 *
 * @since 2.0.0
 */
public class DeviceRequestTargetProcessor extends AbstractDeviceTargetProcessor implements TargetProcessor {
    @Inject
    DeviceRequestManagementService deviceRequestManagementService;
    @Inject
    JobContext jobContext;
    @Inject
    StepContext stepContext;

    @Override
    protected void initProcessing(JobTargetWrapper wrappedJobTarget) {
        setContext(jobContext, stepContext);
    }

    @Override
    public void processTarget(JobTarget jobTarget) throws KapuaException {

        GenericRequestMessage commandInput = stepContextWrapper.getStepProperty(DeviceRequestPropertyKeys.COMMAND_REQUEST_INPUT, GenericRequestMessage.class);
        Long timeout = stepContextWrapper.getStepProperty(DeviceRequestPropertyKeys.TIMEOUT, Long.class);

        KapuaSecurityUtils.doPrivileged(() -> deviceRequestManagementService.exec(jobTarget.getScopeId(), jobTarget.getJobTargetId(), commandInput, timeout));
    }
}

