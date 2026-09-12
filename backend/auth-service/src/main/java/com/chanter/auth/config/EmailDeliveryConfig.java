package com.chanter.auth.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableConfigurationProperties(EmailDeliveryProperties.class)
public class EmailDeliveryConfig {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    @Bean
    public JavaMailSenderImpl transactionalMailSender(
            EmailDeliveryProperties settings,
            @Value("${chanter.public-base-url:http://localhost:5173}") String publicBaseUrl
    ) {
        validate(settings, publicBaseUrl);
        var smtp = settings.smtp();
        var sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        sender.setPort(smtp.port());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (!settings.localSink()) {
            sender.setUsername(smtp.username());
            sender.setPassword(smtp.password());
        }
        var properties = sender.getJavaMailProperties();
        boolean startTls = !settings.localSink() && "starttls".equals(smtp.tlsMode());
        properties.setProperty("mail.smtp.auth", Boolean.toString(!settings.localSink()));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(startTls));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(startTls));
        properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(!settings.localSink() && "implicit".equals(smtp.tlsMode())));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        properties.setProperty("mail.debug", "false");
        return sender;
    }

    private void validate(EmailDeliveryProperties settings, String publicBaseUrl) {
        if (!"smtp".equals(settings.provider())) {
            throw new IllegalStateException("Transactional email requires the smtp provider");
        }
        var smtp = settings.smtp();
        if (smtp == null || blank(smtp.host()) || smtp.port() < 1 || smtp.port() > 65535) {
            throw new IllegalStateException("Transactional email requires a valid SMTP host and port");
        }
        if (!Set.of("starttls", "implicit").contains(smtp.tlsMode() == null ? "" : smtp.tlsMode())) {
            throw new IllegalStateException("SMTP TLS mode must be starttls or implicit");
        }
        validateFrom(settings.from());
        URI origin;
        try {
            origin = URI.create(publicBaseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Public application origin is invalid");
        }
        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))
                || origin.getPort() == 0 || origin.getPort() > 65535
                || !("http".equals(origin.getScheme()) || "https".equals(origin.getScheme()))) {
            throw new IllegalStateException("Public application URL must be an HTTP or HTTPS origin without a path, credentials, query or fragment");
        }
        String smtpHost = smtp.host().toLowerCase(Locale.ROOT);
        boolean localOrigin = LOOPBACK.contains(origin.getHost().toLowerCase(Locale.ROOT));
        if (settings.localSink()) {
            if (!localOrigin || !(LOOPBACK.contains(smtpHost) || "mailpit".equals(smtpHost))) {
                throw new IllegalStateException("Local email sink requires a loopback application origin and loopback or mailpit SMTP host");
            }
        } else {
            if (!"https".equals(origin.getScheme()) || localOrigin) {
                throw new IllegalStateException("Production email links require a non-local HTTPS application origin");
            }
            if (LOOPBACK.contains(smtpHost) || "mailpit".equals(smtpHost) || settings.from().toLowerCase(Locale.ROOT).endsWith(".local")) {
                throw new IllegalStateException("Production email requires a real SMTP provider and sender address");
            }
            if (blank(smtp.username()) || blank(smtp.password())) {
                throw new IllegalStateException("Production SMTP credentials are required");
            }
        }
    }

    private void validateFrom(String from) {
        try {
            if (blank(from) || from.contains("\r") || from.contains("\n")) {
                throw new AddressException();
            }
            InternetAddress address = new InternetAddress(from, true);
            address.validate();
            if (!address.getAddress().equals(from)) {
                throw new AddressException();
            }
        } catch (AddressException exception) {
            throw new IllegalStateException("Transactional email requires a valid sender mailbox address");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
