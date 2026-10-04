package com.nexa.action;

import com.nexa.common.persistence.AssignedIdEntity;
import com.nexa.integration.IntegrationProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** What an executed action created elsewhere: a Jira issue key, a Slack message timestamp. */
@Entity
@Table(name = "external_actions")
public class ExternalAction extends AssignedIdEntity {

    @Column(name = "ai_action_id", nullable = false, updatable = false)
    private UUID aiActionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private IntegrationProvider provider;

    @Column(name = "external_id", nullable = false, updatable = false, length = 200)
    private String externalId;

    @Column(name = "external_url", updatable = false, columnDefinition = "text")
    private String externalUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExternalAction() {
    }

    static ExternalAction record(UUID aiActionId, IntegrationProvider provider, String externalId, String externalUrl,
                                 Instant now) {
        ExternalAction external = new ExternalAction();
        external.assignNewId();
        external.aiActionId = aiActionId;
        external.provider = provider;
        external.externalId = externalId;
        external.externalUrl = externalUrl;
        external.createdAt = now;
        return external;
    }

    public UUID getAiActionId() { return aiActionId; }
    public IntegrationProvider getProvider() { return provider; }
    public String getExternalId() { return externalId; }
    public String getExternalUrl() { return externalUrl; }
}
