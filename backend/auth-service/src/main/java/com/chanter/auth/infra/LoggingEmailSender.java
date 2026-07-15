package com.chanter.auth.infra;

import com.chanter.auth.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chanter.email.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String toEmail, String subject, String bodyText) {
        // Intentionally omit recipient, subject details, and body — auth mails contain secrets (#102).
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("toEmail is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        int bodyLength = bodyText == null ? 0 : bodyText.length();
        log.info("Auth transactional email queued via log provider bodyChars={}", bodyLength);
    }
}
