package com.chanter.auth.infra;

import com.chanter.auth.config.EmailDeliveryProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class SmtpEmailTransport {

    private static final Pattern LINK = Pattern.compile("https?://[^\\s]+");
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailTransport(JavaMailSender mailSender, EmailDeliveryProperties settings) {
        this.mailSender = mailSender;
        this.from = settings.from();
    }

    public void send(String recipient, String subject, String body) {
        var message = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, "Chanter", StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, html(body));
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            // Provider exceptions can contain recipients and message bodies. Never include them in logs.
            throw new MailPreparationException("Could not prepare transactional email");
        }
    }

    private String html(String body) {
        var matcher = LINK.matcher(body);
        var rendered = new StringBuilder();
        int start = 0;
        while (matcher.find()) {
            rendered.append(HtmlUtils.htmlEscape(body.substring(start, matcher.start())));
            String link = HtmlUtils.htmlEscape(matcher.group());
            rendered.append("<a href=\"").append(link).append("\">").append(link).append("</a>");
            start = matcher.end();
        }
        rendered.append(HtmlUtils.htmlEscape(body.substring(start)));
        return "<!doctype html><html lang=\"en\"><body style=\"margin:0;background:#f4f4f5;font-family:Arial,sans-serif;color:#18181b\">"
                + "<main style=\"max-width:560px;margin:32px auto;padding:32px;background:#fff;border-radius:12px\">"
                + "<h1 style=\"font-size:24px;color:#6d28d9\">Chanter</h1><p style=\"line-height:1.6;overflow-wrap:anywhere\">"
                + rendered.toString().replace("\n", "<br>")
                + "</p><hr style=\"border:0;border-top:1px solid #e4e4e7\"><p style=\"font-size:12px;color:#52525b\">"
                + "Your learning community on Chanter</p></main></body></html>";
    }
}
