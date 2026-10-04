package com.nexa.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Development mailer. Links contain secret tokens, so they are only written to the log when
 * {@code nexa.mail.log-links=true}, which is enabled in the local profile only.
 */
@Component
public class LoggingMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailer.class);

    private final boolean logLinks;

    public LoggingMailer(@Value("${nexa.mail.log-links:false}") boolean logLinks) {
        this.logLinks = logLinks;
    }

    @Override
    public void sendPasswordReset(String to, String resetLink) {
        if (logLinks) {
            log.info("[DEV MAIL] Password reset for {}: {}", to, resetLink);
        } else {
            log.warn("Email delivery is not configured; password reset email was not sent");
        }
    }

    @Override
    public void sendInvitation(String to, String organizationName, String inviterName, String acceptLink) {
        if (logLinks) {
            log.info("[DEV MAIL] {} invited {} to {}: {}", inviterName, to, organizationName, acceptLink);
        } else {
            log.warn("Email delivery is not configured; invitation email was not sent");
        }
    }
}
