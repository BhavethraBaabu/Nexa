package com.nexa.action;

import com.nexa.integration.IntegrationProvider;

/**
 * Result of a successful execution: a reference to what was created elsewhere and/or content
 * produced inside Nexa (e.g. an email draft).
 */
public record ActionOutcome(IntegrationProvider provider, String externalId, String externalUrl, String result) {

    public static ActionOutcome external(IntegrationProvider provider, String externalId, String externalUrl) {
        return new ActionOutcome(provider, externalId, externalUrl, null);
    }

    public static ActionOutcome content(String result) {
        return new ActionOutcome(null, null, null, result);
    }
}
