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
package org.eclipse.kapua.service.device.management.request.job.definition;

import com.beust.jcommander.internal.Lists;
import org.eclipse.kapua.service.device.management.request.message.request.JsonGenericRequestMessage;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.device.management.request.DeviceRequestManagementService;
import org.eclipse.kapua.service.device.management.request.job.DeviceRequestTargetProcessor;
import org.eclipse.kapua.service.device.management.request.message.request.GenericRequestMessage;
import org.eclipse.kapua.service.job.step.definition.JobStepDefinition;
import org.eclipse.kapua.service.job.step.definition.JobStepDefinitionRecord;
import org.eclipse.kapua.service.job.step.definition.JobStepPropertyRecord;
import org.eclipse.kapua.service.job.step.definition.JobStepType;
import org.eclipse.kapua.service.job.step.definition.device.management.TimeoutJobStepPropertyRecord;

/**
 * {@link JobStepDefinition} to perform {@link DeviceRequestManagementService#exec(KapuaId, KapuaId, GenericRequestMessage, Long)}.
 *
 * @since 2.0.0
 */
public class DeviceRequestJobStepDefinition extends JobStepDefinitionRecord {

    private static final long serialVersionUID = -6971341093191024211L;

    public DeviceRequestJobStepDefinition() {
        super(null,
                "Generic Device request",
                "Request to execute a generic request on the target devices of the Job",
                JobStepType.TARGET,
                null,
                DeviceRequestTargetProcessor.class.getName(),
                null,
                Lists.newArrayList(
                        new JobStepPropertyRecord(
                                DeviceRequestPropertyKeys.COMMAND_REQUEST_INPUT,
                                "JSON string that defines the generic device request to be executed", //TODO: add json format
                                JsonGenericRequestMessage.class.getName(),
                                null,
                                "{\n" +
                                        "  \"channel\": {\n" +
                                        "    \"type\": \"genericRequestChannel\",\n" +
                                        "    \"method\": \"EXECUTE\",\n" +
                                        "    \"appName\": \"CMD\",\n" +
                                        "    \"version\": \"V1\",\n" +
                                        "    \"resources\": [\n" +
                                        "      \"command\"\n" +
                                        "    ]\n" +
                                        "  },\n" +
                                        "  \"payload\": {\n" +
                                        "    \"metrics\": [\n" +
                                        "      {\n" +
                                        "        \"valueType\": \"string\",\n" +
                                        "        \"value\": \"sleep\",\n" +
                                        "        \"name\": \"command.command\"\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"valueType\": \"string\",\n" +
                                        "        \"value\": \"180\",\n" +
                                        "        \"name\": \"command.argument\"\n" +
                                        "      }\n" +
                                        "    ]\n" +
                                        "  }\n" +
                                        "}",
                                Boolean.TRUE,
                                Boolean.FALSE,
                                null,
                                null,
                                null,
                                null,
                                null),
                        new TimeoutJobStepPropertyRecord()
                )
        );
    }
}