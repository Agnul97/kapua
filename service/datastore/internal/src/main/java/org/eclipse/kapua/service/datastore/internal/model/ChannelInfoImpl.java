/*******************************************************************************
 * Copyright (c) 2016, 2026 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *     Red Hat Inc
 *******************************************************************************/
package org.eclipse.kapua.service.datastore.internal.model;

import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.datastore.model.ChannelInfo;
import org.eclipse.kapua.service.storable.model.id.StorableId;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * {@link ChannelInfo} implementation.
 * <p>
 * Also the JPA entity of the {@code dt_channel_info} table, used when the info docs are stored in SQL ({@code datastore.info.storage=SQL}). The Elasticsearch
 * repositories use the same class and ignore the JPA annotations. Ids are kept as strings and the scope as {@link KapuaEid}, so the columns are plain types;
 * getters and setters keep the {@link StorableId} / {@link KapuaId} API.
 *
 * @since 1.0.0
 */
@Entity(name = "ChannelInfo")
@Table(name = "dt_channel_info")
@Access(AccessType.FIELD)
public class ChannelInfoImpl implements ChannelInfo {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "eid", column = @Column(name = "scope_id", nullable = false, updatable = false))
    })
    private KapuaEid scopeId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private String clientId;

    @Column(name = "channel", nullable = false, updatable = false)
    private String name;

    @Column(name = "first_message_id")
    private String firstMessageId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "first_message_on")
    private Date firstMessageOn;

    @Column(name = "last_message_id")
    private String lastMessageId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_message_on")
    private Date lastMessageOn;

    /**
     * Constructor.
     *
     * @since 1.0.0
     */
    public ChannelInfoImpl() {
    }

    /**
     * Constructor.
     *
     * @param scopeId The scope {@link KapuaId}.
     * @since 1.0.0
     */
    public ChannelInfoImpl(KapuaId scopeId) {
        setScopeId(scopeId);
    }

    @Override
    public StorableId getId() {
        return InfoEntityUtils.toStorableId(id);
    }

    @Override
    public void setId(StorableId id) {
        this.id = InfoEntityUtils.toColumn(id);
    }

    @Override
    public KapuaId getScopeId() {
        return scopeId;
    }

    @Override
    public void setScopeId(KapuaId scopeId) {
        this.scopeId = InfoEntityUtils.toKapuaEid(scopeId);
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public StorableId getFirstMessageId() {
        return InfoEntityUtils.toStorableId(firstMessageId);
    }

    @Override
    public void setFirstMessageId(StorableId firstMessageId) {
        this.firstMessageId = InfoEntityUtils.toColumn(firstMessageId);
    }

    @Override
    public Date getFirstMessageOn() {
        return firstMessageOn;
    }

    @Override
    public void setFirstMessageOn(Date firstMessageOn) {
        this.firstMessageOn = firstMessageOn;
    }

    @Override
    public StorableId getLastMessageId() {
        return InfoEntityUtils.toStorableId(lastMessageId);
    }

    @Override
    public void setLastMessageId(StorableId lastMessageId) {
        this.lastMessageId = InfoEntityUtils.toColumn(lastMessageId);
    }

    @Override
    public Date getLastMessageOn() {
        return lastMessageOn;
    }

    @Override
    public void setLastMessageOn(Date lastMessageOn) {
        this.lastMessageOn = lastMessageOn;
    }
}
