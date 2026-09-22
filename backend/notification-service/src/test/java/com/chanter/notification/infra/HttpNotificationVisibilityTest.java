package com.chanter.notification.infra;

import static org.assertj.core.api.Assertions.*;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.notification.domain.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class HttpNotificationVisibilityTest {
    @Test void sourcePermissionsStatusAndScopeAreCheckedWithDelegatedIdentity() throws Exception {
        UUID user = UUID.randomUUID(), source = UUID.randomUUID(), studyServer = UUID.randomUUID();
        String token = "test-internal-token-with-at-least-32-characters";
        var status = new AtomicInteger(200);
        var response = new AtomicReference<>("{\"id\":\"" + source + "\",\"status\":\"PUBLISHED\"}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var receivedUser = new AtomicReference<String>();
        var receivedToken = new AtomicReference<String>();
        server.createContext("/api/v1/study-servers/" + studyServer + "/events/" + source, exchange -> {
            receivedUser.set(exchange.getRequestHeaders().getFirst(AuthHeaders.USER_ID));
            receivedToken.set(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN));
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var client = new HttpNotificationVisibility(base, base, token);
            var notification = new Notification(UUID.randomUUID(), user, NotificationKind.ANNOUNCEMENT,
                    NotificationFilterBucket.ANNOUNCEMENTS, "Private preview", null, null, "/app/inbox",
                    "COMMUNITY_EVENT", source, studyServer, null, null, null, Instant.now(), null, null);
            assertThat(client.canView(notification)).isTrue();
            assertThat(receivedUser.get()).isEqualTo(user.toString());
            assertThat(receivedToken.get()).isEqualTo(token);
            response.set("{\"id\":\"" + source + "\",\"courseId\":\"" + UUID.randomUUID() + "\"}");
            assertThat(client.canView(notification)).isFalse();
            response.set("{\"id\":\"" + source + "\",\"status\":\"CANCELLED\"}");
            assertThat(client.canView(notification)).isFalse();
            for (int code : new int[] {403, 404,410}) { status.set(code); assertThat(client.canView(notification)).isFalse(); }
            for (int code : new int[] {401, 503}) {
                status.set(code);
                assertThatThrownBy(() -> client.canView(notification)).isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
            }
        } finally { server.stop(0); }
    }
    @Test void answerNotificationUsesExactOwningAnswerRouteAndDelegatedViewer() throws Exception {
        UUID answer=UUID.randomUUID(),channel=UUID.randomUUID(),user=UUID.randomUUID();
        var response=new AtomicReference<>("{\"id\":\""+answer+"\",\"status\":\"AI_ANSWERED\"}");
        var received=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/course-channels/"+channel+"/accepted-answers/"+answer,exchange -> {
            received.set(exchange.getRequestHeaders().getFirst(AuthHeaders.USER_ID));
            byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length); try(var output=exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            var client=new HttpNotificationVisibility("http://127.0.0.1:1",base,"fixture-private-token-long-enough-for-tests");
            var notification=new Notification(UUID.randomUUID(),user,NotificationKind.SUPPORT_QUESTION_ANSWERED,NotificationFilterBucket.MENTIONS,
                    "Answer",null,null,"/app/inbox","STUDY_ASSISTANT_ANSWER",answer,null,null,null,channel,Instant.now(),null,null);
            assertThat(client.canView(notification)).isTrue(); assertThat(received.get()).isEqualTo(user.toString());
            response.set("{\"id\":\""+UUID.randomUUID()+"\",\"status\":\"AI_ANSWERED\"}"); assertThat(client.canView(notification)).isFalse();
        } finally { server.stop(0); }
    }
}
