package com.chanter.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import io.livekit.server.RoomServiceClient;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import livekit.LivekitRtc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

/** Real proxy/server transport proof; full product tests separately cover database suspension authority. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "chanter.livekit.api-key=test-key", "chanter.livekit.api-secret="+LiveKitJoinGuardTest.SECRET})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="LIVEKIT_TEST_BINARY",matches=".+")
@EnabledIfEnvironmentVariable(named="CADDY_TEST_BINARY",matches=".+")
class LiveKitSignalingRevocationTest {
    @LocalServerPort int guardPort;
    @MockitoBean LiveMediaAccess access;

    @Test void actualParticipantIsRemovedAndItsUnexpiredTokenCannotReconnectThroughTheProxy() throws Exception {
        Path artifacts=Path.of("../../.product/media-revocation-"+UUID.randomUUID()).toAbsolutePath().normalize();
        Files.createDirectories(artifacts);
        int mediaPort=freePort(),rtcPort=freePort(),proxyPort=freePort();
        Path mediaConfig=artifacts.resolve("livekit.yaml");
        Files.writeString(mediaConfig,"""
                port: %d
                bind_addresses: [127.0.0.1]
                rtc:
                  tcp_port: %d
                  use_external_ip: false
                  node_ip: 127.0.0.1
                keys:
                  test-key: %s
                logging:
                  level: warn
                """.formatted(mediaPort,rtcPort,LiveKitJoinGuardTest.SECRET));
        Path proxyConfig=artifacts.resolve("Caddyfile");
        Files.writeString(proxyConfig,"""
                {
                  admin off
                  auto_https off
                  persist_config off
                  storage file_system {
                    root "%s"
                  }
                }
                http://127.0.0.1:%d {
                  route /livekit/* {
                    request_header X-Chanter-LiveKit-Token {query.access_token}
                    forward_auth 127.0.0.1:%d {
                      uri /internal/v1/media/join-authorization
                    }
                    request_header -X-Chanter-LiveKit-Token
                    uri strip_prefix /livekit
                    reverse_proxy 127.0.0.1:%d
                  }
                }
                """.formatted(artifacts.resolve("caddy-storage").toString().replace('\\','/'),proxyPort,guardPort,mediaPort));
        Process media=null,proxy=null;
        try (HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            media=new ProcessBuilder(System.getenv("LIVEKIT_TEST_BINARY"),"--config",mediaConfig.toString())
                    .redirectErrorStream(true).redirectOutput(artifacts.resolve("livekit.log").toFile()).start();
            proxy=new ProcessBuilder(System.getenv("CADDY_TEST_BINARY"),"run","--config",proxyConfig.toString(),"--adapter","caddyfile")
                    .redirectErrorStream(true).redirectOutput(artifacts.resolve("caddy.log").toFile()).start();
            awaitServer(http,media,mediaPort);
            awaitServer(http,proxy,proxyPort);
            UUID user=UUID.randomUUID();
            String room="voice-"+UUID.randomUUID();
            String token=LiveKitJoinGuardTest.token(user.toString(),room,true,true,600,LiveKitJoinGuardTest.SECRET);
            URI endpoint=URI.create("ws://127.0.0.1:"+proxyPort+"/livekit/rtc?access_token="+token+"&protocol=15&auto_subscribe=0");
            Participant listener=new Participant();
            WebSocket socket=http.newWebSocketBuilder().buildAsync(endpoint,listener).get(5,TimeUnit.SECONDS);
            try {
                listener.joined.get(5,TimeUnit.SECONDS);
                RoomServiceClient admin=RoomServiceClient.create("http://127.0.0.1:"+mediaPort,"test-key",LiveKitJoinGuardTest.SECRET,
                        false,builder -> builder.callTimeout(2,TimeUnit.SECONDS));
                assertThat(admin.getParticipant(room,user.toString()).execute().isSuccessful()).isTrue();
                doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireAllowed(room,user);
                new LiveMediaReconciler(admin,access).reconcile();
                listener.closed.get(5,TimeUnit.SECONDS);
                assertThat(admin.getParticipant(room,user.toString()).execute().code()).isEqualTo(404);
                assertThatThrownBy(() -> http.newWebSocketBuilder().buildAsync(endpoint,new Participant()).join())
                        .hasCauseInstanceOf(WebSocketHandshakeException.class)
                        .satisfies(error -> assertThat(((WebSocketHandshakeException)error.getCause()).getResponse().statusCode()).isEqualTo(403));
                assertThat(Files.readString(artifacts.resolve("caddy.log"))).doesNotContain(token);
                assertThat(Files.readString(artifacts.resolve("livekit.log"))).doesNotContain(token);
            } finally { socket.abort(); }
        } finally {
            stopOwned(proxy);
            stopOwned(media);
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket=new ServerSocket(0)) { return socket.getLocalPort(); }
    }
    private static void awaitServer(HttpClient http,Process owned,int port) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
        while(System.nanoTime()<deadline) {
            if(!owned.isAlive()) throw new IllegalStateException("Owned media test process exited before readiness");
            try {
                http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port)).timeout(Duration.ofSeconds(1)).build(),
                        HttpResponse.BodyHandlers.discarding());
                return;
            } catch(java.io.IOException unavailable) { Thread.sleep(100); }
        }
        throw new IllegalStateException("Owned media test process did not become ready");
    }
    private static void stopOwned(Process process) throws Exception {
        if(process==null) return;
        process.destroy();
        if(!process.waitFor(5,TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(5,TimeUnit.SECONDS); }
    }
    private static class Participant implements WebSocket.Listener {
        final CompletableFuture<Void> joined=new CompletableFuture<>();
        final CompletableFuture<Integer> closed=new CompletableFuture<>();
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        @Override public CompletionStage<?> onBinary(WebSocket socket,ByteBuffer data,boolean last) {
            try {
                if(bytes.size()+data.remaining()>1024*1024) throw new IllegalStateException("Unexpected media test frame size");
                while(data.hasRemaining()) bytes.write(data.get());
                if(last) {
                    if(LivekitRtc.SignalResponse.parseFrom(bytes.toByteArray()).hasJoin()) joined.complete(null);
                    bytes.reset();
                }
            } catch(Exception invalid) { joined.completeExceptionally(invalid); }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket socket,int status,String reason) {
            closed.complete(status);
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket socket,Throwable error) {
            joined.completeExceptionally(error); closed.completeExceptionally(error);
        }
    }
}
