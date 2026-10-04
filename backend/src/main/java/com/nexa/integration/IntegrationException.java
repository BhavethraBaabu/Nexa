package com.nexa.integration;

/**
 * A failed call to an external service. {@link #userMessage()} is safe to show and store;
 * provider response bodies are never included.
 */
public class IntegrationException extends RuntimeException {

    public enum Code {
        NOT_CONNECTED(false),
        NOT_CONFIGURED(false),
        /** Credentials revoked or refresh failed: an admin must reconnect. */
        AUTH_FAILED(false),
        /** The provider rejected the request content (e.g. missing project permission). */
        REJECTED(false),
        RATE_LIMITED(true),
        UNAVAILABLE(true),
        FAILED(true);

        private final boolean retryable;

        Code(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    private final Code code;
    private final String userMessage;

    public IntegrationException(Code code, String userMessage) {
        this(code, userMessage, null);
    }

    public IntegrationException(Code code, String userMessage, Throwable cause) {
        super(code + ": " + userMessage, cause);
        this.code = code;
        this.userMessage = userMessage;
    }

    public Code code() {
        return code;
    }

    public String userMessage() {
        return userMessage;
    }

    public boolean retryable() {
        return code.retryable();
    }
}
