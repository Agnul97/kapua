/*******************************************************************************
 * Copyright (c) 2018, 2020 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.device.call.message.lifecycle;

import org.eclipse.kapua.service.device.call.message.DeviceChannel;

/**
 * {@link DeviceLifecycleChannel} definition.
 *
 * @since 1.0.0
 */
public interface DeviceLifecycleChannel extends DeviceChannel {

    /**
     * Gets the lifecycle phase.
     * <p>
     * Lifecycle has many phases that determines different aspects of the application.
     *
     * @return The phase.
     * @since 1.2.0
     */
    String getPhase();
}
