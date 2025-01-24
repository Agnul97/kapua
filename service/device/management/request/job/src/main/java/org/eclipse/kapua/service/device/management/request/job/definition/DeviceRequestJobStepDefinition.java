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
                                "XML string that defines the generic device request to be executed", //TODO: add json format
                                GenericRequestMessage.class.getName(),
                                null,
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                        "<genericRequestMessage>\n" +
                                        "    <scopeId>scopeId</scopeId>\n" +
                                        "    <deviceId>deviceId</deviceId>\n" +
                                        "    <channel xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"genericRequestChannel\">\n" +
                                        "        <method>EXECUTE</method>\n" +
                                        "        <appName>CMD</appName>\n" +
                                        "        <version>V1</version>\n" +
                                        "        <resources>command</resources>\n" +
                                        "    </channel>\n" +
                                        "    <payload xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"genericRequestPayload\">\n" +
                                        "        <metrics>\n" +
                                        "            <metric>\n" +
                                        "                <name>command.command</name>\n" +
                                        "                <valueType>string</valueType>\n" +
                                        "                <value>ls</value>\n" +
                                        "            </metric>\n" +
                                        "            <metric>\n" +
                                        "                <name>command.argument</name>\n" +
                                        "                <valueType>string</valueType>\n" +
                                        "                <value>-l</value>\n" +
                                        "            </metric>\n" +
                                        "        </metrics>\n" +
                                        "    </payload>\n" +
                                        "</genericRequestMessage>", //TODO: add json format example
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