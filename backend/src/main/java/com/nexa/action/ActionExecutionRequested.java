package com.nexa.action;

import java.util.UUID;

/** PRD section 36: ActionExecutionRequested. Delivered after the approving transaction commits. */
public record ActionExecutionRequested(UUID actionId) {
}
