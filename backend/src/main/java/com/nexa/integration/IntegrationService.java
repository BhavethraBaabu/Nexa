package com.nexa.integration;

import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.common.exception.ResourceNotFoundException;
import com.nexa.common.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IntegrationService {

    /** Refresh a little early so a token never expires mid-request. */
    private static final Duration REFRESH_SKEW = Duration.ofMinutes(2);

    private final IntegrationRepository repository;
    private final TokenCipher cipher;
    private final IntegrationProperties properties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Map<IntegrationProvider, TokenRefresher> refreshers;
    private final Clock clock;

    public IntegrationService(IntegrationRepository repository, TokenCipher cipher, IntegrationProperties properties,
                              AuditService auditService, ObjectMapper objectMapper, List<TokenRefresher> refreshers,
                              Clock clock) {
        this.repository = repository;
        this.cipher = cipher;
        this.properties = properties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.refreshers = refreshers.stream().collect(Collectors.toMap(TokenRefresher::provider, Function.identity()));
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IntegrationResponse> list(AuthenticatedUser current) {
        Map<IntegrationProvider, Integration> connected = repository.findByOrganizationId(current.organizationId()).stream()
                .collect(Collectors.toMap(Integration::getProvider, Function.identity()));
        List<IntegrationResponse> result = new ArrayList<>();
        for (IntegrationProvider provider : IntegrationProvider.values()) {
            Integration i = connected.get(provider);
            boolean available = switch (provider) {
                case JIRA -> properties.jira().configured();
                case SLACK -> properties.slack().configured();
                case GITHUB -> false; // MVP+ (PRD section 20)
            };
            result.add(i == null
                    ? new IntegrationResponse(provider, available, false, null, null, false, null, null)
                    : new IntegrationResponse(provider, available, true, i.getStatus(), i.getExternalWorkspaceName(),
                            configComplete(i), config(i), i.getCreatedAt()));
        }
        return result;
    }

    /** Stores credentials from a completed OAuth flow, replacing any previous connection. */
    @Transactional
    public void saveConnection(UUID organizationId, UUID userId, IntegrationProvider provider, String accessToken,
                               String refreshToken, Instant expiresAt, String workspaceId, String workspaceName) {
        Instant now = clock.instant();
        Integration integration = repository.findByOrganizationIdAndProvider(organizationId, provider)
                .orElseGet(() -> Integration.connect(organizationId, provider, userId, now));
        boolean workspaceChanged = integration.getExternalWorkspaceId() != null
                && !integration.getExternalWorkspaceId().equals(workspaceId);
        integration.updateCredentials(cipher.encrypt(accessToken), cipher.encrypt(refreshToken), expiresAt,
                workspaceId, workspaceName, userId, now);
        if (workspaceChanged) {
            integration.updateConfig("{}", now); // Old project/channel IDs mean nothing in another workspace.
        }
        repository.save(integration);
        auditService.record(organizationId, userId, AuditAction.INTEGRATION_CONNECTED, "INTEGRATION", integration.getId(),
                Map.of("provider", provider.name(), "workspace", workspaceName == null ? workspaceId : workspaceName));
    }

    @Transactional
    public void disconnect(AuthenticatedUser current, IntegrationProvider provider) {
        Integration integration = load(current.organizationId(), provider);
        repository.delete(integration);
        auditService.record(current.organizationId(), current.userId(), AuditAction.INTEGRATION_DISCONNECTED, "INTEGRATION",
                integration.getId(), Map.of("provider", provider.name()));
    }

    @Transactional
    public IntegrationResponse updateConfig(AuthenticatedUser current, IntegrationProvider provider, Object config) {
        Integration integration = load(current.organizationId(), provider);
        integration.updateConfig(objectMapper.writeValueAsString(config), clock.instant());
        auditService.record(current.organizationId(), current.userId(), AuditAction.INTEGRATION_CONFIGURED, "INTEGRATION",
                integration.getId(), Map.of("provider", provider.name()));
        return list(current).stream().filter(r -> r.provider() == provider).findFirst().orElseThrow();
    }

    /** Config written by the system itself (e.g. the Jira site URL found during connection). */
    @Transactional
    public void updateConfigInternal(UUID integrationId, Object config) {
        repository.findById(integrationId).ifPresent(i -> i.updateConfig(objectMapper.writeValueAsString(config), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Integration load(UUID organizationId, IntegrationProvider provider) {
        return repository.findByOrganizationIdAndProvider(organizationId, provider)
                .orElseThrow(() -> new ResourceNotFoundException("Integration", provider.name().toLowerCase()));
    }

    public <T> T config(Integration integration, Class<T> type) {
        return objectMapper.readValue(integration.getConfig(), type);
    }

    /**
     * Returns a usable access token, refreshing it first if it is about to expire. A failed
     * refresh marks the integration as needing reconnection.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = IntegrationException.class)
    public String accessToken(UUID integrationId) {
        Integration integration = repository.findForUpdate(integrationId)
                .orElseThrow(() -> new IntegrationException(IntegrationException.Code.NOT_CONNECTED, "The integration was disconnected."));
        if (integration.getStatus() != IntegrationStatus.CONNECTED) {
            throw new IntegrationException(IntegrationException.Code.AUTH_FAILED, reconnectMessage(integration.getProvider()));
        }
        Instant now = clock.instant();
        boolean expiring = integration.getExpiresAt() != null && !now.plus(REFRESH_SKEW).isBefore(integration.getExpiresAt());
        if (!expiring) {
            return cipher.decrypt(integration.getEncryptedAccessToken());
        }
        TokenRefresher refresher = refreshers.get(integration.getProvider());
        String refreshToken = cipher.decrypt(integration.getEncryptedRefreshToken());
        if (refresher == null || refreshToken == null) {
            integration.markError(now);
            throw new IntegrationException(IntegrationException.Code.AUTH_FAILED, reconnectMessage(integration.getProvider()));
        }
        try {
            TokenRefresher.Tokens tokens = refresher.refresh(refreshToken);
            integration.refreshTokens(cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()),
                    tokens.expiresAt(), now);
            return tokens.accessToken();
        } catch (IntegrationException e) {
            if (e.code() == IntegrationException.Code.AUTH_FAILED) {
                integration.markError(now);
            }
            throw e;
        }
    }

    /** Called when a provider rejects an otherwise valid token (revoked access). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(UUID integrationId) {
        repository.findById(integrationId).ifPresent(i -> i.markError(clock.instant()));
    }

    /** Why actions for {@code provider} can't run yet, or null if they can. */
    @Transactional(readOnly = true)
    public String blockedReason(UUID organizationId, IntegrationProvider provider) {
        String name = provider == IntegrationProvider.JIRA ? "Jira" : "Slack";
        Integration integration = repository.findByOrganizationIdAndProvider(organizationId, provider).orElse(null);
        if (integration == null) {
            return "Connect " + name + " in Integrations first.";
        }
        if (integration.getStatus() != IntegrationStatus.CONNECTED) {
            return reconnectMessage(provider);
        }
        if (!configComplete(integration)) {
            return provider == IntegrationProvider.JIRA
                    ? "Choose a Jira project and issue type in Integrations first."
                    : "Choose a Slack channel in Integrations first.";
        }
        return null;
    }

    public static String reconnectMessage(IntegrationProvider provider) {
        String name = provider == IntegrationProvider.JIRA ? "Jira" : provider == IntegrationProvider.SLACK ? "Slack" : "GitHub";
        return name + " access has expired or was revoked. An admin needs to reconnect " + name + " in Integrations.";
    }

    private boolean configComplete(Integration i) {
        return switch (i.getProvider()) {
            case JIRA -> config(i, IntegrationConfigs.JiraConfig.class).complete();
            case SLACK -> config(i, IntegrationConfigs.SlackConfig.class).complete();
            case GITHUB -> false;
        };
    }

    private Object config(Integration i) {
        return switch (i.getProvider()) {
            case JIRA -> config(i, IntegrationConfigs.JiraConfig.class);
            case SLACK -> config(i, IntegrationConfigs.SlackConfig.class);
            case GITHUB -> Map.of();
        };
    }
}
