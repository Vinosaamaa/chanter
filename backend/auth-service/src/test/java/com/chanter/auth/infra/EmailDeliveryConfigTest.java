package com.chanter.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chanter.auth.config.EmailDeliveryConfig;
import com.chanter.auth.config.EmailDeliveryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailDeliveryConfigTest {

    @Test
    void productionRequiresEncryptedAuthenticatedSmtpAndAnHttpsApplicationOrigin() {
        var properties = properties(false, "smtp.provider.test", "sender@chanter.test", "user", "password", "starttls");
        JavaMailSenderImpl sender = new EmailDeliveryConfig().transactionalMailSender(properties, "https://learn.chanter.test");

        assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.smtp.timeout", "5000")
                .containsEntry("mail.smtp.writetimeout", "5000")
                .containsEntry("mail.debug", "false");
        assertThatThrownBy(() -> new EmailDeliveryConfig().transactionalMailSender(properties, "http://learn.chanter.test"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTPS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "https://learn.chanter.test/path", "https://learn.chanter.test?token=secret",
            "https://user:password@learn.chanter.test", "https://learn.chanter.test#fragment"})
    void productionRejectsUnsafeApplicationOrigins(String origin) {
        var properties = properties(false, "smtp.provider.test", "sender@chanter.test", "user", "password", "starttls");
        assertThatThrownBy(() -> new EmailDeliveryConfig().transactionalMailSender(properties, origin))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secret");
    }

    @Test
    void localSinkRequiresLocalSmtpAndLocalWebOrigins() {
        var local = properties(true, "mailpit", "noreply@chanter.local", "", "", "starttls");
        assertThat(new EmailDeliveryConfig().transactionalMailSender(local, "http://localhost:5173")
                .getJavaMailProperties()).containsEntry("mail.smtp.auth", "false")
                .containsEntry("mail.smtp.starttls.enable", "false");
        assertThatThrownBy(() -> new EmailDeliveryConfig().transactionalMailSender(local, "https://learn.chanter.test"))
                .isInstanceOf(IllegalStateException.class);
        var remote = properties(true, "smtp.provider.test", "noreply@chanter.local", "", "", "starttls");
        assertThatThrownBy(() -> new EmailDeliveryConfig().transactionalMailSender(remote, "http://localhost:5173"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionRejectsMissingCredentialsAndInvalidTlsModeWithoutExposingConfiguration() {
        var noCredentials = properties(false, "smtp.provider.test", "sender@chanter.test", "", "", "starttls");
        assertThatThrownBy(() -> new EmailDeliveryConfig().transactionalMailSender(noCredentials, "https://learn.chanter.test"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("credentials");
        var invalidTls = properties(false, "smtp.provider.test", "sender@chanter.test", "user", "secret-password", "none");
        assertThatThrownBy(() -> new EmailDeliveryConfig().transactionalMailSender(invalidTls, "https://learn.chanter.test"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secret-password");
        assertThat(invalidTls.toString()).doesNotContain("secret-password", "smtp.provider.test", "sender@chanter.test");
    }

    @Test
    void implicitTlsKeepsCertificateVerificationEnabled() {
        var properties = properties(false, "smtp.provider.test", "sender@chanter.test", "user", "password", "implicit");
        assertThat(new EmailDeliveryConfig().transactionalMailSender(properties, "https://learn.chanter.test")
                .getJavaMailProperties()).containsEntry("mail.smtp.ssl.enable", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true");
    }

    private EmailDeliveryProperties properties(boolean local, String host, String from, String username,
                                                String password, String tlsMode) {
        return new EmailDeliveryProperties("smtp", from, local,
                new EmailDeliveryProperties.Smtp(host, 587, username, password, tlsMode));
    }
}
