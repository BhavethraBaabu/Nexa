package com.nexa.integration.jira;

import com.nexa.action.ActionContext;
import com.nexa.action.ActionHandler;
import com.nexa.action.ActionOutcome;
import com.nexa.action.ActionType;
import com.nexa.common.security.SecureTokens;
import com.nexa.integration.Integration;
import com.nexa.integration.IntegrationConfigs.JiraConfig;
import com.nexa.integration.IntegrationException;
import com.nexa.integration.IntegrationProvider;
import com.nexa.integration.IntegrationRepository;
import com.nexa.integration.IntegrationService;
import com.nexa.task.Priority;
import com.nexa.task.Task;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Approved task → Jira issue (PRD section 17). */
@Component
class JiraIssueHandler implements ActionHandler {

    private static final DateTimeFormatter MEETING_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final IntegrationRepository integrations;
    private final IntegrationService integrationService;
    private final JiraClient jira;

    JiraIssueHandler(IntegrationRepository integrations, IntegrationService integrationService, JiraClient jira) {
        this.integrations = integrations;
        this.integrationService = integrationService;
        this.jira = jira;
    }

    @Override
    public ActionType type() {
        return ActionType.CREATE_JIRA_ISSUE;
    }

    @Override
    public ActionOutcome execute(ActionContext context) {
        Integration integration = integrations.findByOrganizationIdAndProvider(context.action().getOrganizationId(), IntegrationProvider.JIRA)
                .orElseThrow(() -> new IntegrationException(IntegrationException.Code.NOT_CONNECTED, "Jira isn't connected. An admin can connect it in Integrations."));
        JiraConfig config = integrationService.config(integration, JiraConfig.class);
        if (!config.complete()) {
            throw new IntegrationException(IntegrationException.Code.NOT_CONFIGURED, "Choose a Jira project and issue type in Integrations.");
        }
        String cloudId = integration.getExternalWorkspaceId();
        String token = integrationService.accessToken(integration.getId());
        String label = label(context);

        try {
            // Idempotency (PRD section 52): an earlier, interrupted attempt may already have created it.
            var existing = jira.findByLabel(cloudId, config.siteUrl(), token, label);
            if (existing.isPresent()) {
                return ActionOutcome.external(IntegrationProvider.JIRA, existing.get().key(), existing.get().url());
            }
            Task task = context.task();
            String assignee = context.owner() == null ? null
                    : jira.findAccountIdByEmail(cloudId, token, context.owner().getEmail()).orElse(null);
            JiraClient.CreatedIssue issue = jira.createIssue(cloudId, config.siteUrl(), token, new JiraClient.IssueRequest(
                    config.projectKey(), config.issueTypeId(), task.getTitle(), description(context),
                    priority(task.getPriority(), config.defaultPriority()),
                    task.getDeadline() == null ? null : task.getDeadline().toString(),
                    assignee, List.of("nexa", label)));
            return ActionOutcome.external(IntegrationProvider.JIRA, issue.key(), issue.url());
        } catch (IntegrationException e) {
            if (e.code() == IntegrationException.Code.AUTH_FAILED) {
                integrationService.markError(integration.getId());
            }
            throw e;
        }
    }

    /** A stable, unique label per action; Jira labels can't contain spaces. */
    static String label(ActionContext context) {
        return "nexa-" + SecureTokens.hash(context.action().getIdempotencyKey()).substring(0, 16);
    }

    private static List<String> description(ActionContext context) {
        Task task = context.task();
        List<String> lines = new ArrayList<>();
        if (task.getDescription() != null) {
            lines.add(task.getDescription());
        }
        String owner = context.owner() != null ? context.owner().getName() : task.getOwnerName() != null ? task.getOwnerName() : "Unassigned";
        lines.add("Owner: " + owner);
        if (task.getDeadline() != null) {
            lines.add("Due: " + task.getDeadline());
        } else if (task.getDeadlineText() != null) {
            lines.add("Timing mentioned: \"" + task.getDeadlineText() + "\" (needs a date)");
        }
        lines.add("Source meeting: " + context.meeting().getTitle() + " (" + context.meeting().getMeetingDate().format(MEETING_DATE) + ")");
        if (task.getEvidence() != null) {
            lines.add("From the transcript: \"" + task.getEvidence() + "\"");
        }
        lines.add("Created by Nexa from an approved meeting action.");
        return lines;
    }

    private static String priority(Priority priority, String fallback) {
        return switch (priority) {
            case HIGH -> "High";
            case LOW -> "Low";
            case MEDIUM -> fallback == null ? "Medium" : fallback;
        };
    }
}
