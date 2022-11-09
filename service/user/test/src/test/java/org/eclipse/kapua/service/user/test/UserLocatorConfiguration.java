/*******************************************************************************
 * Copyright (c) 2019, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.service.user.test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import io.cucumber.java.Before;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.configuration.AccountChildrenFinder;
import org.eclipse.kapua.commons.configuration.ResourceLimitedServiceConfigurationManagerBase;
import org.eclipse.kapua.commons.configuration.RootUserTester;
import org.eclipse.kapua.commons.configuration.UsedEntitiesCounterImpl;
import org.eclipse.kapua.commons.configuration.metatype.KapuaMetatypeFactoryImpl;
import org.eclipse.kapua.commons.jpa.EntityManagerSession;
import org.eclipse.kapua.commons.model.query.QueryFactoryImpl;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.config.metatype.KapuaMetatypeFactory;
import org.eclipse.kapua.model.query.QueryFactory;
import org.eclipse.kapua.qa.common.MockedLocator;
import org.eclipse.kapua.service.authorization.AuthorizationService;
import org.eclipse.kapua.service.authorization.permission.Permission;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.user.UserDomains;
import org.eclipse.kapua.service.user.UserFactory;
import org.eclipse.kapua.service.user.UserNamedEntityService;
import org.eclipse.kapua.service.user.UserService;
import org.eclipse.kapua.service.user.internal.UserCacheFactory;
import org.eclipse.kapua.service.user.internal.UserDAO;
import org.eclipse.kapua.service.user.internal.UserEntityManagerFactory;
import org.eclipse.kapua.service.user.internal.UserFactoryImpl;
import org.eclipse.kapua.service.user.internal.UserNamedEntityServiceImpl;
import org.eclipse.kapua.service.user.internal.UserServiceImpl;
import org.mockito.Matchers;
import org.mockito.Mockito;

@Singleton
public class UserLocatorConfiguration {

    /**
     * Setup DI with Google Guice DI.
     * Create mocked and non mocked service under test and bind them with Guice.
     * It is based on custom MockedLocator locator that is meant for sevice unit tests.
     */
    @Before(value = "@setup", order = 1)
    public void setupDI() {
        MockedLocator mockedLocator = (MockedLocator) KapuaLocator.getInstance();

        AbstractModule module = new AbstractModule() {

            @Override
            protected void configure() {
                // Inject mocked Authorization Service method checkPermission
                AuthorizationService mockedAuthorization = Mockito.mock(AuthorizationService.class);
                try {
                    Mockito.doNothing().when(mockedAuthorization).checkPermission(Matchers.any(Permission.class));
                } catch (KapuaException e) {
                    // skip
                }

                bind(QueryFactory.class).toInstance(new QueryFactoryImpl());

                bind(AuthorizationService.class).toInstance(mockedAuthorization);
                // Inject mocked Permission Factory
                PermissionFactory mockPermissionFactory = Mockito.mock(PermissionFactory.class);
                bind(PermissionFactory.class).toInstance(mockPermissionFactory);
                // Set KapuaMetatypeFactory for Metatype configuration
                bind(KapuaMetatypeFactory.class).toInstance(new KapuaMetatypeFactoryImpl());

                // binding Account related services
                final AccountChildrenFinder accountChildrenFinder = Mockito.mock(AccountChildrenFinder.class);
                bind(AccountChildrenFinder.class).toInstance(accountChildrenFinder);

                // Inject actual User service related services
                UserEntityManagerFactory userEntityManagerFactory = new UserEntityManagerFactory();
                bind(UserEntityManagerFactory.class).toInstance(userEntityManagerFactory);
                final UserFactoryImpl userFactory = new UserFactoryImpl();
                bind(UserFactory.class).toInstance(userFactory);
                final RootUserTester mockRootUserTester = Mockito.mock(RootUserTester.class);
                bind(RootUserTester.class).toInstance(mockRootUserTester);
                final UserNamedEntityService namedEntityService = new UserNamedEntityServiceImpl(userEntityManagerFactory, new UserCacheFactory(), mockPermissionFactory, mockedAuthorization);
                bind(UserNamedEntityService.class).toInstance(namedEntityService);
                final ResourceLimitedServiceConfigurationManagerBase userConfigurationManager = new ResourceLimitedServiceConfigurationManagerBase(UserService.class.getName(),
                        UserDomains.USER_DOMAIN,
                        new EntityManagerSession(userEntityManagerFactory),
                        mockPermissionFactory,
                        mockedAuthorization,
                        Mockito.mock(RootUserTester.class),
                        accountChildrenFinder,
                        new UsedEntitiesCounterImpl(
                                userFactory,
                                UserDomains.USER_DOMAIN,
                                UserDAO::count,
                                mockedAuthorization,
                                mockPermissionFactory,
                                new EntityManagerSession(userEntityManagerFactory))
                ) {
                };
                bind(UserService.class).toInstance(
                        new UserServiceImpl(
                                mockedAuthorization,
                                mockPermissionFactory,
                                userEntityManagerFactory,
                                new UserCacheFactory(),
                                namedEntityService,
                                userConfigurationManager)
                );
            }
        };

        Injector injector = Guice.createInjector(module);
        mockedLocator.setInjector(injector);
    }
}
