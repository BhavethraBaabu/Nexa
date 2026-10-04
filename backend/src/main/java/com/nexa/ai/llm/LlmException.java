package com.nexa.ai.llm;

/**
 * A failed model call. {@link #userMessage()} is safe to show to users and store; the cause
 * (which may contain provider details) is only logged.
 */
public class LlmException extends RuntimeException {

    public enum Code {
        NOT_CONFIGURED("AI analysis isn't set up on this server yet. An administrator needs to add an API key.", false),
        AUTHENTICATION("The AI provider rejected Nexa's credentials. An administrator needs to check the API key.", false),
        RATE_LIMITED("The AI provider is busy right now. Please try again in a minute.", true),
        UNAVAILABLE("The AI provider is temporarily unavailable. Please try again shortly.", true),
        REFUSED("The AI provider declined to analyze this transcript.", false),
        TRUNCATED("The analysis was too long to complete. Try splitting the transcript into smaller meetings.", false),
        INVALID_RESPONSE("The AI returned a result Nexa couldn't read. Please try again.", true),
        FAILED("Nexa couldn't analyze this meeting. Please try again.", true);

        private final String userMessage;
        private final boolean retryable;

        Code(String userMessage, boolean retryable) {
            this.userMessage = userMessage;
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    private final Code code;

    public LlmException(Code code, String detail, Throwable cause) {
        super(code + ": " + detail, cause);
        this.code = code;
    }

    public LlmException(Code code, String detail) {
        this(code, detail, null);
    }

    public Code code() {
        return code;
    }

    public String userMessage() {
        return code.userMessage;
    }

    public boolean retryable() {
        return code.retryable;
    }
}
