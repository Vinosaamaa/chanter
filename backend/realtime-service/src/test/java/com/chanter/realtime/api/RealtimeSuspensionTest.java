package com.chanter.realtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.chanter.common.auth.JwtTokenService;
import com.chanter.common.auth.ModerationAccess;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealtimeSuspensionTest {
    @LocalServerPort int port;
    @Autowired JwtTokenService tokens;
    @MockitoBean ModerationAccess moderation;

    @RepeatedTest(3) void alreadyConnectedIdleClientIsClosedWhenItsAccountBecomesSuspended() throws Exception {
        UUID user = UUID.randomUUID();
        String authorization="Bearer "+tokens.createAccessToken(user,UUID.randomUUID());
        AtomicInteger checks = new AtomicInteger();
        doAnswer(call -> {
            if (checks.incrementAndGet() > 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return null;
        }).when(moderation).requireSession(authorization,user);
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        try (var client=HttpClient.newHttpClient()) {
            WebSocket socket=client.newWebSocketBuilder().header("Authorization",authorization)
                    .buildAsync(URI.create("ws://localhost:"+port+"/api/v1/realtime/ws"),new WebSocket.Listener(){
                        @Override public java.util.concurrent.CompletionStage<?> onClose(WebSocket socket,int code,String reason){
                            closed.complete(code); return CompletableFuture.completedFuture(null);
                        }
                        @Override public void onError(WebSocket socket,Throwable error){closed.completeExceptionally(error);}
                    }).get(5,TimeUnit.SECONDS);
            try {
                assertThat(closed.get(10,TimeUnit.SECONDS)).isEqualTo(1008);
                assertThat(checks.get()).isGreaterThanOrEqualTo(2);
            } finally { socket.abort(); }
        }
    }
}
