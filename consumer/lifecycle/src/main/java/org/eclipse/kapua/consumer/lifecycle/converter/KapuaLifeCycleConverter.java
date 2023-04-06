/*******************************************************************************
 * Copyright (c) 2016, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.consumer.lifecycle.converter;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.metric.MetricsLabel;
import org.eclipse.kapua.service.camel.converter.AbstractKapuaConverter;
import org.eclipse.kapua.service.camel.message.CamelKapuaMessage;
import org.eclipse.kapua.service.client.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;

/**
 * Kapua message converter used to convert life cycle messages.
 *
 * @since 1.0
 */
public class KapuaLifeCycleConverter extends AbstractKapuaConverter {

    public static final Logger logger = LoggerFactory.getLogger(KapuaLifeCycleConverter.class);

    private Counter metricConverterAppMessage;
    private Counter metricConverterBirthMessage;
    private Counter metricConverterDcMessage;
    private Counter metricConverterMissingMessage;
    private Counter metricConverterNotifyMessage;

    public KapuaLifeCycleConverter() {
        super();
        metricConverterAppMessage = METRICS_SERVICE.getCounter(MetricsLabel.MODULE_CONVERTER, MetricsLabel.COMPONENT_KAPUA, MetricsLabel.KAPUA_MESSAGE, MetricsLabel.MESSAGES, MetricsLabel.MESSAGE_APPS, MetricsLabel.COUNT);
        metricConverterBirthMessage = METRICS_SERVICE.getCounter(MetricsLabel.MODULE_CONVERTER, MetricsLabel.COMPONENT_KAPUA, MetricsLabel.KAPUA_MESSAGE, MetricsLabel.MESSAGES, MetricsLabel.MESSAGE_BIRTH, MetricsLabel.COUNT);
        metricConverterDcMessage = METRICS_SERVICE.getCounter(MetricsLabel.MODULE_CONVERTER, MetricsLabel.COMPONENT_KAPUA, MetricsLabel.KAPUA_MESSAGE, MetricsLabel.MESSAGES, MetricsLabel.MESSAGE_DC, MetricsLabel.COUNT);
        metricConverterMissingMessage = METRICS_SERVICE.getCounter(MetricsLabel.MODULE_CONVERTER, MetricsLabel.COMPONENT_KAPUA, MetricsLabel.KAPUA_MESSAGE, MetricsLabel.MESSAGES, MetricsLabel.MESSAGE_MISSING, MetricsLabel.COUNT);
        metricConverterNotifyMessage = METRICS_SERVICE.getCounter(MetricsLabel.MODULE_CONVERTER, MetricsLabel.COMPONENT_KAPUA, MetricsLabel.KAPUA_MESSAGE, MetricsLabel.MESSAGES, MetricsLabel.MESSAGE_NOTIFY, MetricsLabel.COUNT);
    }

    /**
     * Convert incoming message to a Kapua application (life cycle) message
     *
     * @param exchange
     * @param value
     * @return Message container that contains application message
     * @throws KapuaException
     *             if incoming message does not contain a javax.jms.BytesMessage or an error during conversion occurred
     */
    @Converter
    public CamelKapuaMessage<?> convertToApps(Exchange exchange, Object value) throws KapuaException {
        metricConverterAppMessage.inc();
        return convertTo(exchange, value, MessageType.APP);
    }

    /**
     * Convert incoming message to a Kapua birth (life cycle) message
     *
     * @param exchange
     * @param value
     * @return Message container that contains birth message
     * @throws KapuaException
     *             if incoming message does not contain a javax.jms.BytesMessage or an error during conversion occurred
     */
    @Converter
    public CamelKapuaMessage<?> convertToBirth(Exchange exchange, Object value) throws KapuaException {
        metricConverterBirthMessage.inc();
        return convertTo(exchange, value, MessageType.BIRTH);
    }

    /**
     * Convert incoming message to a Kapua disconnect (life cycle) message
     *
     * @param exchange
     * @param value
     * @return Message container that contains disconnect message
     * @throws KapuaException
     *             if incoming message does not contain a javax.jms.BytesMessage or an error during conversion occurred
     */
    @Converter
    public CamelKapuaMessage<?> convertToDisconnect(Exchange exchange, Object value) throws KapuaException {
        metricConverterDcMessage.inc();
        return convertTo(exchange, value, MessageType.DISCONNECT);
    }

    /**
     * Convert incoming message to a Kapua missing (life cycle) message
     *
     * @param exchange
     * @param value
     * @return Message container that contains missing message
     * @throws KapuaException
     *             if incoming message does not contain a javax.jms.BytesMessage or an error during conversion occurred
     */
    @Converter
    public CamelKapuaMessage<?> convertToMissing(Exchange exchange, Object value) throws KapuaException {
        metricConverterMissingMessage.inc();
        return convertTo(exchange, value, MessageType.MISSING);
    }

    /**
     * Convert incoming message to a Kapua notify (life cycle) message
     *
     * @param exchange
     * @param value
     * @return
     * @throws KapuaException
     */
    @Converter
    public CamelKapuaMessage<?> convertToNotify(Exchange exchange, Object value) throws KapuaException {
        metricConverterNotifyMessage.inc();
        return convertTo(exchange, value, MessageType.NOTIFY);
    }

}
