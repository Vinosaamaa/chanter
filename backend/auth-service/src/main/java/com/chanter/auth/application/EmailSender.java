package com.chanter.auth.application;

import java.time.Instant;

public interface EmailSender {

    /** Queue a transactional message. Provider delivery happens after the caller commits. */
    void send(String toEmail, String subject, String bodyText);

    /** The queue must stop attempting delivery when the message's link expires. */
    default void send(String toEmail, String subject, String bodyText, Instant expiresAt) {
        send(toEmail, subject, bodyText);
    }
}
