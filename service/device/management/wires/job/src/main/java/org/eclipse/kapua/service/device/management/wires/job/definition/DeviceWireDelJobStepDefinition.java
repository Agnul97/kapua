/*******************************************************************************
 * Copyright (c) 2024, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.service.device.management.wires.job.definition;

import com.beust.jcommander.internal.Lists;
import org.eclipse.kapua.service.device.management.wires.job.DeviceWireConfigurationDeleteTargetProcessor;
import org.eclipse.kapua.service.job.step.definition.JobStepDefinitionRecord;
import org.eclipse.kapua.service.job.step.definition.JobStepType;
import org.eclipse.kapua.service.job.step.definition.device.management.TimeoutJobStepPropertyRecord;

public class DeviceWireDelJobStepDefinition extends JobStepDefinitionRecord {

    public DeviceWireDelJobStepDefinition() {
        super(null,
                "Wire graph delete",
                "Request to delete the wire graph to the target devices of the Job",
                JobStepType.TARGET,
                null,
                DeviceWireConfigurationDeleteTargetProcessor.class.getName(),
                null,
                Lists.newArrayList(
                        new TimeoutJobStepPropertyRecord()
                )
        );
    }
}
