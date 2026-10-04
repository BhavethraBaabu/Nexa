package com.nexa.integration;

import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A connected workspace. Token columns hold ciphertext only (see {@link TokenCipher}). */
@Entity
@Table(name = "integrations")
public class Integration extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private IntegrationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationStatus status;

    @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "text")
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", columnDefinition = "text")
    private String encryptedRefreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "external_workspace_id", nullable = false, length = 100)
    private String externalWorkspaceId;

    @Column(name = "external_workspace_name", length = 200)
    private String externalWorkspaceName;

    @Column(nullable = false, columnDefinition = "text")
    private String config;

    @Column(name = "connected_by", nullable = false)
    private UUID connectedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Integration() {
    }

    static Integration connect(UUID organizationId, IntegrationProvider provider, UUID connectedBy, Instant now) {
        Integration integration = new Integration();
        integration.assignNewId();
        integration.organizationId = organizationId;
        integration.provider = provider;
        integration.connectedBy = connectedBy;
        integration.config = "{}";
        integration.createdAt = now;
        return integration;
    }

    void updateCredentials(String encryptedAccess, String encryptedRefresh, Instant expiresAt, String workspaceId,
                           String workspaceName, UUID connectedBy, Instant now) {
        this.encryptedAccessToken = encryptedAccess;
        this.encryptedRefreshToken = encryptedRefresh;
        this.expiresAt = expiresAt;
        this.externalWorkspaceId = workspaceId;
        this.externalWorkspaceName = workspaceName;
        this.connectedBy = connectedBy;
        this.status = IntegrationStatus.CONNECTED;
        this.updatedAt = now;
    }

    void refreshTokens(String encryptedAccess, String encryptedRefresh, Instant expiresAt, Instant now) {
        this.encryptedAccessToken = encryptedAccess;
        if (encryptedRefresh != null) {
            this.encryptedRefreshToken = encryptedRefresh;
        }
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    void markError(Instant now) {
        this.status = IntegrationStatus.ERROR;
        this.updatedAt = now;
    }

    void updateConfig(String configJson, Instant now) {
        this.config = configJson;
        this.updatedAt = now;
    }

    public UUID getOrganizationId() { return organizationId; }
    public IntegrationProvider getProvider() { return provider; }
    public IntegrationStatus getStatus() { return status; }
    String getEncryptedAccessToken() { return encryptedAccessToken; }
    String getEncryptedRefreshToken() { return encryptedRefreshToken; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getExternalWorkspaceId() { return externalWorkspaceId; }
    public String getExternalWorkspaceName() { return externalWorkspaceName; }
    public String getConfig() { return config; }
    public UUID getConnectedBy() { return connectedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
