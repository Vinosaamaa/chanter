package com.chanter.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.auth.config.EmailDeliveryConfig;
import com.chanter.auth.config.EmailDeliveryProperties;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SmtpEmailTransportTest {

    @Test
    void deliversBrandedMultipartMessageToALoopbackSmtpSink() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(10000);
            var received = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    var output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                    output.print("220 localhost test sink\r\n");
                    output.flush();
                    StringBuilder data = new StringBuilder();
                    boolean inData = false;
                    String line;
                    while ((line = input.readLine()) != null) {
                        if (inData && !line.equals(".")) {
                            data.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                            continue;
                        }
                        if (inData) {
                            inData = false;
                            output.print("250 accepted\r\n");
                        } else if (line.equals("DATA")) {
                            inData = true;
                            output.print("354 send data\r\n");
                        } else if (line.equals("QUIT")) {
                            output.print("221 bye\r\n");
                            output.flush();
                            break;
                        } else {
                            output.print("250 localhost\r\n");
                        }
                        output.flush();
                    }
                    return new MimeMessage(Session.getInstance(new Properties()),
                            new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8)));
                }
            });
            var properties = new EmailDeliveryProperties("smtp", "noreply@chanter.local", true,
                    new EmailDeliveryProperties.Smtp("127.0.0.1", server.getLocalPort(), "", "", "starttls"));
            var mailSender = new EmailDeliveryConfig().transactionalMailSender(properties, "http://localhost:5173");
            var transport = new SmtpEmailTransport(mailSender, properties);
            String body = "Welcome to Chanter.\n\nVerify your email:\nhttp://localhost:5173/verify-email?token=test-token\n\n<unsafe>";

            transport.send("learner@example.test", "Verify your Chanter email", body);

            MimeMessage message = received.get(10, TimeUnit.SECONDS);
            assertThat(message.getSubject()).isEqualTo("Verify your Chanter email");
            assertThat(message.getFrom()[0].toString()).isEqualTo("Chanter <noreply@chanter.local>");
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo("learner@example.test");
            Multipart mixed = (Multipart) message.getContent();
            Multipart alternatives = (Multipart) mixed.getBodyPart(0).getContent();
            assertThat(alternatives.getBodyPart(0).getContent().toString().replace("\r\n", "\n")).isEqualTo(body);
            assertThat(alternatives.getBodyPart(1).getContent().toString())
                    .contains("Chanter", "href=\"http://localhost:5173/verify-email?token=test-token\"", "&lt;unsafe&gt;")
                    .doesNotContain("<unsafe>");
        }
    }
}
