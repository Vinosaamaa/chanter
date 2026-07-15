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
        // Do not log message bodies — they contain one-time auth tokens (#102 / SAST).
        int bodyLength = bodyText == null ? 0 : bodyText.length();
        log.info(
                "Auth email queued via log provider to={} subject={} bodyChars={}",
                toEmail,
                subject,
                bodyLength
        );
    }
}
