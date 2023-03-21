/*******************************************************************************
 * Copyright (c) 2021, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.service.job.step.definition.internal;

import com.google.inject.Provides;
import org.eclipse.kapua.commons.core.AbstractKapuaModule;
import org.eclipse.kapua.commons.jpa.DuplicateNameCheckerImpl;
import org.eclipse.kapua.commons.jpa.JpaTxManager;
import org.eclipse.kapua.commons.jpa.KapuaEntityManagerFactory;
import org.eclipse.kapua.commons.jpa.KapuaJpaRepositoryConfiguration;
import org.eclipse.kapua.service.authorization.AuthorizationService;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.job.step.definition.JobStepDefinitionFactory;
import org.eclipse.kapua.service.job.step.definition.JobStepDefinitionRepository;
import org.eclipse.kapua.service.job.step.definition.JobStepDefinitionService;

import javax.inject.Inject;

public class JobStepDefinitionModule extends AbstractKapuaModule {
    @Override
    protected void configureModule() {
        bind(JobStepDefinitionFactory.class).to(JobStepDefinitionFactoryImpl.class);
    }

    @Provides
    @Inject
    JobStepDefinitionService jobStepDefinitionService(
            AuthorizationService authorizationService,
            PermissionFactory permissionFactory,
            JobStepDefinitionRepository repository) {
        return new JobStepDefinitionServiceImpl(
                authorizationService,
                permissionFactory,
                new JpaTxManager(new KapuaEntityManagerFactory("kapua-job")),
                repository,
                new DuplicateNameCheckerImpl<>(repository, scopeId -> new JobStepDefinitionQueryImpl(scopeId)));
    }

    @Provides
    @Inject
    JobStepDefinitionRepository jobStepDefinitionRepository(KapuaJpaRepositoryConfiguration jpaRepoConfig) {
        return new JobStepDefinitionImplJpaRepository(jpaRepoConfig);
    }

}
