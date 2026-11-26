/*******************************************************************************
 * Copyright (c) 2026 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.integration.service.datastore;

import org.junit.Ignore;
import org.junit.runner.RunWith;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;

// Disabled while the info docs run on SQL: LastPublishedTrackerSteps reads, deletes and counts versions of the channel info doc directly in
// Elasticsearch (unwrapped RestClient on the channel index, _version). To re-enable, go through ChannelInfoRepository instead of the ES client.
@Ignore("Reads the channel info doc directly from Elasticsearch: not compatible with datastore.info.storage=SQL")
@RunWith(Cucumber.class)
@CucumberOptions(
        features = "classpath:features/datastore/DatastoreLastPublishedTracker.feature",
        glue = {"org.eclipse.kapua.qa.common",
                "org.eclipse.kapua.qa.integration.steps",
                "org.eclipse.kapua.service.account.steps",
                "org.eclipse.kapua.service.user.steps",
                "org.eclipse.kapua.service.datastore.steps",
                "org.eclipse.kapua.service.device.registry.steps"},
        plugin = {"pretty",
                "html:target/cucumber/DatastoreLastPublishedTracker",
                "json:target/DatastoreLastPublishedTracker_cucumber.json"},
        monochrome = true)
public class RunDatastoreLastPublishedTrackerTest {
}
