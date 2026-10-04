package com.nexa.integration;

/** Admin-chosen settings stored as JSON in {@code integrations.config}. */
public final class IntegrationConfigs {

    private IntegrationConfigs() {
    }

    /** PRD section 17: project, issue type and default priority. */
    public record JiraConfig(String siteUrl, String projectKey, String projectName, String issueTypeId,
                             String issueTypeName, String defaultPriority) {

        public boolean complete() {
            return projectKey != null && !projectKey.isBlank() && issueTypeId != null && !issueTypeId.isBlank();
        }
    }

    /** PRD section 18: the channel notifications go to. */
    public record SlackConfig(String channelId, String channelName) {

        public boolean complete() {
            return channelId != null && !channelId.isBlank();
        }
    }
}
