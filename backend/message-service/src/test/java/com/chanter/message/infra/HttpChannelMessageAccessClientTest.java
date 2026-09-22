package com.chanter.message.infra;

import static org.assertj.core.api.Assertions.assertThat;
import com.chanter.message.domain.ChannelScope;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpChannelMessageAccessClientTest {
    @Test void carriesOwningServerFromTheAuthorizedCourseChannelResponse() throws Exception {
        UUID server=UUID.randomUUID(),course=UUID.randomUUID(),channel=UUID.randomUUID();
        var http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/api/v1/course-channels/"+channel+"/channel-message-access",exchange -> {
            byte[] body=("{\"channelId\":\""+channel+"\",\"courseId\":\""+course+"\",\"studyServerId\":\""+server
                    +"\",\"channelName\":\"discussion\",\"canReadMessages\":true,\"canPostMessages\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        http.start();
        try {
            var client=new HttpChannelMessageAccessClient("http://127.0.0.1:"+http.getAddress().getPort(),"test-internal-service-token-for-message");
            var access=client.requireAccess(channel,UUID.randomUUID(),ChannelScope.COURSE);
            assertThat(access.studyServerId()).isEqualTo(server);
            assertThat(access.courseId()).isEqualTo(course);
            assertThat(access.canPostMessages()).isTrue();
        } finally { http.stop(0); }
    }
}
