package com.nexa.notification;

/**
 * Outbound transactional email. Real delivery (SES/SMTP) is added in a later phase;
 * until then {@link LoggingMailer} is used.
 */
public interface Mailer {

    void sendPasswordReset(String to, String resetLink);

    void sendInvitation(String to, String organizationName, String inviterName, String acceptLink);
}
