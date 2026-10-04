package com.nexa.action;

/**
 * The tool interface between approved actions and external systems (PRD section 4.5:
 * Action → Tool Interface → Integration Adapter → External Service). The action engine knows
 * nothing about Jira or Slack; each adapter implements this.
 * <p>
 * Implementations must be idempotent: the engine may call {@link #execute} again for an action
 * whose previous attempt was interrupted.
 */
public interface ActionHandler {

    ActionType type();

    /**
     * @throws com.nexa.integration.IntegrationException with a user-safe message on failure
     */
    ActionOutcome execute(ActionContext context);
}
