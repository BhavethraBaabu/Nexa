package com.nexa.integration.slack;

import com.nexa.action.ActionContext;
import com.nexa.action.ActionHandler;
import com.nexa.action.ActionOutcome;
import com.nexa.action.ActionType;
import com.nexa.integration.Integration;
import com.nexa.integration.IntegrationConfigs.SlackConfig;
import com.nexa.integration.IntegrationException;
import com.nexa.integration.IntegrationProvider;
import com.nexa.integration.IntegrationRepository;
import com.nexa.integration.IntegrationService;
import com.nexa.task.Task;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Approved task → Slack notification (PRD section 18). */
@Component
class SlackMessageHandler implements ActionHandler {

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);

    private final IntegrationRepository integrations;
    private final IntegrationService integrationService;
    private final SlackClient slack;

    SlackMessageHandler(IntegrationRepository integrations, IntegrationService integrationService, SlackClient slack) {
        this.integrations = integrations;
        this.integrationService = integrationService;
        this.slack = slack;
    }

    @Override
    public ActionType type() {
        return ActionType.SEND_SLACK_MESSAGE;
    }

    @Override
    public ActionOutcome execute(ActionContext context) {
        Integration integration = integrations.findByOrganizationIdAndProvider(context.action().getOrganizationId(), IntegrationProvider.SLACK)
                .orElseThrow(() -> new IntegrationException(IntegrationException.Code.NOT_CONNECTED, "Slack isn't connected. An admin can connect it in Integrations."));
        SlackConfig config = integrationService.config(integration, SlackConfig.class);
        if (!config.complete()) {
            throw new IntegrationException(IntegrationException.Code.NOT_CONFIGURED, "Choose a Slack channel in Integrations.");
        }
        Task task = context.task();
        String owner = context.owner() != null ? context.owner().getName() : task.getOwnerName() != null ? task.getOwnerName() : "Unassigned";
        String due = task.getDeadline() != null ? task.getDeadline().format(DUE) : "Not set";
        String priority = task.getPriority().name().charAt(0) + task.getPriority().name().substring(1).toLowerCase(Locale.ROOT);
        String text = """
                :pushpin: *Nexa Meeting Action*
                *Task:* %s
                *Owner:* %s
                *Due:* %s
                *Priority:* %s
                *Source:* %s""".formatted(escape(task.getTitle()), escape(owner), due, priority, escape(context.meeting().getTitle()));
        String fallback = "Nexa action: " + task.getTitle() + " (" + owner + ", due " + due + ")";
        try {
            SlackClient.PostedMessage message = slack.postMessage(integrationService.accessToken(integration.getId()), config.channelId(),
                    fallback, List.of(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", text))));
            // External ID is channel:ts, which uniquely identifies a Slack message.
            return ActionOutcome.external(IntegrationProvider.SLACK, message.channel() + ":" + message.ts(), message.permalink());
        } catch (IntegrationException e) {
            if (e.code() == IntegrationException.Code.AUTH_FAILED) {
                integrationService.markError(integration.getId());
            }
            throw e;
        }
    }

    /** Slack mrkdwn control characters (https://api.slack.com/reference/surfaces/formatting#escaping). */
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
