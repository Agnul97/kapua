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
package org.eclipse.kapua.service.tag.internal;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.KapuaIllegalNullArgumentException;
import org.eclipse.kapua.commons.configuration.ServiceConfigurationManager;
import org.eclipse.kapua.model.domain.Actions;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.id.KapuaIdImpl;
import org.eclipse.kapua.service.authorization.AuthorizationService;
import org.eclipse.kapua.service.authorization.permission.Permission;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.tag.Tag;
import org.eclipse.kapua.service.tag.TagDomains;
import org.eclipse.kapua.service.tag.TagFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockSettings;
import org.mockito.Mockito;

import java.math.BigInteger;


public class TagServiceRepoBasedImplTest {

    private static final MockSettings FAIL_ON_UNPREDICTED_METHOD_CALL = Mockito.withSettings()
            .defaultAnswer(invocation -> {
                throw new UnsupportedOperationException(invocation.toString());
            });
    public static final Permission FAKE_PERMISSION = new StubPermission("fakeDomain", Actions.execute, new KapuaIdImpl(BigInteger.ONE), new KapuaIdImpl(BigInteger.TEN), true);
    private PermissionFactory permissionFactory;
    private AuthorizationService authorizationService;
    private ServiceConfigurationManager serviceConfigurationManager;
    private TagRepository tagRepository;
    private TagServiceRepoBasedImpl instance;
    private TagFactory tagFactory;

    @BeforeEach
    public void setUp() throws KapuaException {
        permissionFactory = Mockito.mock(PermissionFactory.class);
        Mockito.when(permissionFactory.newPermission(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(FAKE_PERMISSION);
        authorizationService = Mockito.mock(AuthorizationService.class);
        serviceConfigurationManager = Mockito.mock(ServiceConfigurationManager.class);
        tagRepository = Mockito.mock(TagRepository.class);
        Mockito.when(tagRepository.create(Mockito.<Tag>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        tagFactory = Mockito.mock(TagFactory.class);
        Mockito.when(tagFactory.newCreator(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> new TagCreatorImpl(invocation.getArgument(0), invocation.getArgument(1)));
        Mockito.when(tagFactory.newEntity(Mockito.any()))
                .thenAnswer(invocation -> new TagImpl(invocation.<KapuaId>getArgument(0)));
        instance = new TagServiceRepoBasedImpl(
                permissionFactory,
                authorizationService,
                serviceConfigurationManager,
                tagRepository,
                tagFactory);
    }

    @Test
    public void performsInputValidation() {
        Assertions.assertThrows(KapuaIllegalNullArgumentException.class,
                () -> instance.create(null),
                "Does not accept null tagCreator");
        Assertions.assertThrows(KapuaIllegalNullArgumentException.class,
                () -> instance.create(new TagCreatorImpl(null, "testTag")),
                "Does not accept tagCreator with null scope id");
        Assertions.assertThrows(KapuaIllegalNullArgumentException.class,
                () -> instance.create(new TagCreatorImpl(new KapuaIdImpl(BigInteger.ONE), null)),
                "Does not accept tagCreator with null name");
    }

    @Test
    public void callsCollaboratorsAsExpected() throws KapuaException {
        final KapuaIdImpl scopeId = new KapuaIdImpl(BigInteger.ONE);

        final Tag got = instance.create(new TagCreatorImpl(scopeId, "testTag"));
        Assertions.assertEquals(scopeId, got.getScopeId());
        Assertions.assertEquals("tag", got.getType());
        Assertions.assertEquals("testTag", got.getName());

        Mockito.verify(permissionFactory).newPermission(Mockito.eq(TagDomains.TAG_DOMAIN), Mockito.eq(Actions.write), Mockito.eq(scopeId));
        Mockito.verify(permissionFactory).newPermission(Mockito.eq(TagDomains.TAG_DOMAIN), Mockito.eq(Actions.read), Mockito.eq(scopeId));
        Mockito.verify(authorizationService, Mockito.times(2)).checkPermission(Mockito.eq(FAKE_PERMISSION));
        Mockito.verify(serviceConfigurationManager).checkAllowedEntities(Mockito.eq(scopeId), Mockito.any());
        Mockito.verify(tagRepository).create(Mockito.<Tag>any());
        Mockito.verify(tagRepository).count(Mockito.any());
        Mockito.verify(tagFactory).newEntity(scopeId);
        Mockito.verifyNoMoreInteractions(serviceConfigurationManager);
        Mockito.verifyNoMoreInteractions(permissionFactory);
        Mockito.verifyNoMoreInteractions(authorizationService);
        Mockito.verifyNoMoreInteractions(tagRepository);
        Mockito.verifyNoMoreInteractions(tagFactory);
    }
}