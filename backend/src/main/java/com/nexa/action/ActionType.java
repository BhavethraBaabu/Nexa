package com.nexa.action;

import com.nexa.integration.IntegrationProvider;

/** What an approved action does (PRD section 15). */
public enum ActionType {
    CREATE_JIRA_ISSUE(IntegrationProvider.JIRA, true),
    SEND_SLACK_MESSAGE(IntegrationProvider.SLACK, true),
    /** Draft only in the MVP (PRD section 19): produces text for a person to send. */
    DRAFT_EMAIL(null, false);

    private final IntegrationProvider provider;
    private final boolean perTask;

    ActionType(IntegrationProvider provider, boolean perTask) {
        this.provider = provider;
        this.perTask = perTask;
    }

    /** The integration this action needs, or null if it runs entirely inside Nexa. */
    public IntegrationProvider provider() {
        return provider;
    }

    public boolean perTask() {
        return perTask;
    }
}
