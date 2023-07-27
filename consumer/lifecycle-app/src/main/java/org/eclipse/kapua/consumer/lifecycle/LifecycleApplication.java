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
package org.eclipse.kapua.consumer.lifecycle;

import org.eclipse.kapua.commons.metric.CommonsMetric;
import org.eclipse.kapua.service.camel.setting.ServiceSettingKey;
import org.eclipse.kapua.service.security.SecurityUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;

/**
 * Lifecycle application container main class
 *
 */
@ImportResource({"classpath:spring/applicationContext.xml"})
@PropertySource(value = "classpath:spring/application.properties")
@SpringBootApplication
public class LifecycleApplication {

    public LifecycleApplication() {
        SecurityUtil.initSecurityManager();
    }

    public void doNothing() { //TODO : CODE SMELL HERE
        //spring needs a public constructor but our checkstyle doesn't allow a class with only static methods and a public constructor
    }

    public static void main(String[] args) { //TODO : CODE SMELL HERE 2
        //TODO to be injected!!!
        CommonsMetric.module = MetricsLifecycle.CONSUMER_LIFECYCLE;
        //statically set parameters
        System.setProperty(ServiceSettingKey.JAXB_CONTEXT_CLASS_NAME.key(), LifecycleJAXBContextProvider.class.getName());
        //org.springframework.context.ApplicationContext is not needed now so don't keep the SpringApplication.run return
        SpringApplication.run(LifecycleApplication.class, args);
    }

}
