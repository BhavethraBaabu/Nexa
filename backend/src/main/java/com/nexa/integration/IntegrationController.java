package com.nexa.integration;

import com.nexa.common.exception.InvalidRequestException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.integration.jira.JiraClient;
import com.nexa.integration.slack.SlackClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Integration management (PRD sections 17, 18, 29, 45). Connecting, configuring and
 * disconnecting are ADMIN-only. OAuth callbacks are public browser redirects authenticated by
 * the signed {@code state}.
 */
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {

    private static final Logger log = LoggerFactory.getLogger(IntegrationController.class);

    private final IntegrationService service;
    private final IntegrationProperties properties;
    private final OAuthStateService stateService;
    private final JiraClient jira;
    private final SlackClient slack;

    public IntegrationController(IntegrationService service, IntegrationProperties properties, OAuthStateService stateService,
                                 JiraClient jira, SlackClient slack) {
        this.service = service;
        this.properties = properties;
        this.stateService = stateService;
        this.jira = jira;
        this.slack = slack;
    }

    public record AuthorizeResponse(String authorizeUrl) {
    }

    public record JiraConfigRequest(@NotBlank String projectKey, String projectName, @NotBlank String issueTypeId,
                                    String issueTypeName, @Pattern(regexp = "Highest|High|Medium|Low|Lowest") String defaultPriority) {
    }

    public record SlackConfigRequest(@NotBlank String channelId, String channelName) {
    }

    @GetMapping
    public List<IntegrationResponse> list(@AuthenticationPrincipal AuthenticatedUser current) {
        return service.list(current);
    }

    // --- Jira -------------------------------------------------------------------------------

    @PostMapping("/jira/connect")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthorizeResponse connectJira(@AuthenticationPrincipal AuthenticatedUser current) {
        if (!properties.jira().configured()) {
            throw new InvalidRequestException("Jira isn't set up on this server. Add JIRA_CLIENT_ID and JIRA_CLIENT_SECRET.");
        }
        return new AuthorizeResponse(jira.authorizeUrl(stateService.issue(current.organizationId(), current.userId(), IntegrationProvider.JIRA)));
    }

    @GetMapping("/jira/callback")
    public ResponseEntity<Void> jiraCallback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                             @RequestParam(required = false) String error) {
        return callback(IntegrationProvider.JIRA, code, state, error, () -> {
            OAuthStateService.State verified = stateService.verify(state, IntegrationProvider.JIRA);
            com.nexa.integration.TokenRefresher.Tokens tokens = jira.exchangeCode(code);
            JiraClient.Site site = jira.primarySite(tokens.accessToken());
            service.saveConnection(verified.organizationId(), verified.userId(), IntegrationProvider.JIRA, tokens.accessToken(),
                    tokens.refreshToken(), tokens.expiresAt(), site.cloudId(), site.name());
            Integration integration = service.load(verified.organizationId(), IntegrationProvider.JIRA);
            IntegrationConfigs.JiraConfig existing = service.config(integration, IntegrationConfigs.JiraConfig.class);
            service.updateConfigInternal(integration.getId(), new IntegrationConfigs.JiraConfig(site.url(), existing.projectKey(),
                    existing.projectName(), existing.issueTypeId(), existing.issueTypeName(), existing.defaultPriority()));
        });
    }

    @GetMapping("/jira/projects")
    @PreAuthorize("hasRole('ADMIN')")
    public List<JiraClient.Project> jiraProjects(@AuthenticationPrincipal AuthenticatedUser current) {
        Integration integration = service.load(current.organizationId(), IntegrationProvider.JIRA);
        return asUserError(() -> jira.projects(integration.getExternalWorkspaceId(), service.accessToken(integration.getId())));
    }

    @GetMapping("/jira/projects/{projectKey}/issue-types")
    @PreAuthorize("hasRole('ADMIN')")
    public List<JiraClient.IssueType> jiraIssueTypes(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable String projectKey) {
        if (!projectKey.matches("[A-Za-z0-9_]{1,20}")) {
            throw new InvalidRequestException("Invalid project key");
        }
        Integration integration = service.load(current.organizationId(), IntegrationProvider.JIRA);
        return asUserError(() -> jira.issueTypes(integration.getExternalWorkspaceId(), service.accessToken(integration.getId()), projectKey));
    }

    @PutMapping("/jira/config")
    @PreAuthorize("hasRole('ADMIN')")
    public IntegrationResponse configureJira(@AuthenticationPrincipal AuthenticatedUser current, @Valid @RequestBody JiraConfigRequest request) {
        Integration integration = service.load(current.organizationId(), IntegrationProvider.JIRA);
        String siteUrl = service.config(integration, IntegrationConfigs.JiraConfig.class).siteUrl();
        return service.updateConfig(current, IntegrationProvider.JIRA, new IntegrationConfigs.JiraConfig(siteUrl, request.projectKey(),
                request.projectName(), request.issueTypeId(), request.issueTypeName(),
                request.defaultPriority() == null ? "Medium" : request.defaultPriority()));
    }

    @DeleteMapping("/jira")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnectJira(@AuthenticationPrincipal AuthenticatedUser current) {
        service.disconnect(current, IntegrationProvider.JIRA);
    }

    // --- Slack ------------------------------------------------------------------------------

    @PostMapping("/slack/connect")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthorizeResponse connectSlack(@AuthenticationPrincipal AuthenticatedUser current) {
        if (!properties.slack().configured()) {
            throw new InvalidRequestException("Slack isn't set up on this server. Add SLACK_CLIENT_ID and SLACK_CLIENT_SECRET.");
        }
        return new AuthorizeResponse(slack.authorizeUrl(stateService.issue(current.organizationId(), current.userId(), IntegrationProvider.SLACK)));
    }

    @GetMapping("/slack/callback")
    public ResponseEntity<Void> slackCallback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                              @RequestParam(required = false) String error) {
        return callback(IntegrationProvider.SLACK, code, state, error, () -> {
            OAuthStateService.State verified = stateService.verify(state, IntegrationProvider.SLACK);
            SlackClient.Installation installation = slack.exchangeCode(code);
            // Slack bot tokens don't expire unless token rotation is enabled on the app.
            service.saveConnection(verified.organizationId(), verified.userId(), IntegrationProvider.SLACK,
                    installation.accessToken(), null, null, installation.teamId(), installation.teamName());
        });
    }

    @GetMapping("/slack/channels")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SlackClient.Channel> slackChannels(@AuthenticationPrincipal AuthenticatedUser current) {
        Integration integration = service.load(current.organizationId(), IntegrationProvider.SLACK);
        return asUserError(() -> slack.channels(service.accessToken(integration.getId())));
    }

    @PutMapping("/slack/config")
    @PreAuthorize("hasRole('ADMIN')")
    public IntegrationResponse configureSlack(@AuthenticationPrincipal AuthenticatedUser current, @Valid @RequestBody SlackConfigRequest request) {
        return service.updateConfig(current, IntegrationProvider.SLACK, new IntegrationConfigs.SlackConfig(request.channelId(), request.channelName()));
    }

    @DeleteMapping("/slack")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnectSlack(@AuthenticationPrincipal AuthenticatedUser current) {
        service.disconnect(current, IntegrationProvider.SLACK);
    }

    // --- Helpers ----------------------------------------------------------------------------

    /** Finishes an OAuth flow and sends the browser back to the Integrations page with the outcome. */
    private ResponseEntity<Void> callback(IntegrationProvider provider, String code, String state, String error, Runnable complete) {
        String outcome;
        String message = null;
        if (error != null || code == null || state == null) {
            outcome = "cancelled";
        } else {
            try {
                complete.run();
                outcome = "connected";
            } catch (IntegrationException e) {
                outcome = "error";
                message = e.userMessage();
                log.warn("{} OAuth callback failed: {}", provider, e.code());
            } catch (com.nexa.common.exception.NexaException e) {
                outcome = "error";
                message = e.getMessage();
            }
        }
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(properties.frontendBaseUrl()).path("/integrations")
                .queryParam("provider", provider.name().toLowerCase()).queryParam("result", outcome);
        if (message != null) {
            target.queryParam("message", message);
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target.encode().build().toUriString()).build();
    }

    private static <T> T asUserError(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (IntegrationException e) {
            throw new InvalidRequestException(e.userMessage());
        }
    }
}
