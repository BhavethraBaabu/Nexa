package com.nexa.integration;

public enum IntegrationStatus {
    CONNECTED,
    /** Credentials stopped working (revoked or refresh failed); an admin must reconnect. */
    ERROR
}
