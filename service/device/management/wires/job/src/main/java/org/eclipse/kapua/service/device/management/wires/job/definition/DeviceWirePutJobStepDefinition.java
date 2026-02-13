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
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.device.management.configuration.DeviceConfiguration;
import org.eclipse.kapua.service.device.management.wire.DeviceWiresManagementService;
import org.eclipse.kapua.service.device.management.wires.job.DeviceWireConfigurationPutTargetProcessor;
import org.eclipse.kapua.service.job.step.definition.JobStepDefinition;
import org.eclipse.kapua.service.job.step.definition.JobStepDefinitionRecord;
import org.eclipse.kapua.service.job.step.definition.JobStepPropertyRecord;
import org.eclipse.kapua.service.job.step.definition.JobStepType;
import org.eclipse.kapua.service.job.step.definition.device.management.TimeoutJobStepPropertyRecord;

/**
 * {@link JobStepDefinition} to perform {@link DeviceWiresManagementService#put(KapuaId, KapuaId, DeviceConfiguration, Long)}.
 *
 * @since 2.1.0
 */
public class DeviceWirePutJobStepDefinition extends JobStepDefinitionRecord {

    public DeviceWirePutJobStepDefinition() {
        super(null,
                "Wire graph put",
                "Request to execute a wire graph update to the target devices of the Job",
                JobStepType.TARGET,
                null,
                DeviceWireConfigurationPutTargetProcessor.class.getName(),
                null,
                Lists.newArrayList(
                        new JobStepPropertyRecord(
                                DeviceWiresPropertyKeys.WIRE_CONFIG,
                                "XML or JSON string that defines the wire graph update sent to the target devices",
                                DeviceConfiguration.class.getName(),
                                null,
                                "{  \"type\": \"deviceConfiguration\",  \"configuration\": [    {      \"id\": \"org.eclipse.kura.wire.graph.WireGraphService\",      \"properties\": {        \"property\": [          {            \"name\": \"WireGraph\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"{\\\"components\\\":[{\\\"pid\\\":\\\"exampleTimer\\\",\\\"inputPortCount\\\":0,\\\"outputPortCount\\\":1,\\\"renderingProperties\\\":{\\\"position\\\":{\\\"x\\\":-80,\\\"y\\\":-40},\\\"inputPortNames\\\":{},\\\"outputPortNames\\\":{}}}],\\\"wires\\\":[]}\"            ]          }        ]      }    },    {      \"id\": \"exampleTimer\",      \"properties\": {        \"property\": [          {            \"name\": \"factoryPid\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"org.eclipse.kura.wire.Timer\"            ]          },          {            \"name\": \"componentId\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"exampleTimer\"            ]          },          {            \"name\": \"factoryComponent\",            \"array\": false,            \"encrypted\": false,            \"type\": \"Boolean\",            \"value\": [              \"false\"            ]          },          {            \"name\": \"simple.first.tick.policy\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"DEFAULT\"            ]          },          {            \"name\": \"componentDescription\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"A wire component that fires a ticking event on every configured interval\"            ]          },          {            \"name\": \"simple.interval\",            \"array\": false,            \"encrypted\": false,            \"type\": \"Integer\",            \"value\": [              \"10\"            ]          },          {            \"name\": \"simple.time.unit\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"SECONDS\"            ]          },          {            \"name\": \"type\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"SIMPLE\"            ]          },          {            \"name\": \"service.factoryPid\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"org.eclipse.kura.wire.Timer\"            ]          },          {            \"name\": \"cron.interval\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"0/10 * * * * ?\"            ]          },          {            \"name\": \"kura.service.pid\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"exampleTimer\"            ]          },          {            \"name\": \"service.pid\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"org.eclipse.kura.wire.Timer-1770978370301-20\"            ]          },          {            \"name\": \"emitter.port.count\",            \"array\": false,            \"encrypted\": false,            \"type\": \"Integer\",            \"value\": [              \"1\"            ]          },          {            \"name\": \"receiver.port.count\",            \"array\": false,            \"encrypted\": false,            \"type\": \"Integer\",            \"value\": [              \"0\"            ]          },          {            \"name\": \"componentName\",            \"array\": false,            \"encrypted\": false,            \"type\": \"String\",            \"value\": [              \"Timer\"            ]          },          {            \"name\": \"simple.custom.first.tick.interval\",            \"array\": false,            \"encrypted\": false,            \"type\": \"Integer\",            \"value\": [              \"0\"            ]          }        ]      }    }  ]}",
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
