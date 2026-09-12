package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;

import java.io.DataInputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ClamAvScannerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void streamsBytesAndDistinguishesMalwareFromClean(boolean infected) throws Exception {
        try (var server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            var serving = CompletableFuture.runAsync(() -> {
                try {
                    try (var socket = server.accept()) {
                        assertThat(readCommand(socket.getInputStream())).isEqualTo("zVERSION");
                        String date = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.US).withZone(ZoneOffset.UTC).format(Instant.now());
                        socket.getOutputStream().write(("ClamAV 1.5.2/28000/" + date + "\0").getBytes(StandardCharsets.US_ASCII));
                    }
                    try (var socket = server.accept()) {
                        assertThat(readCommand(socket.getInputStream())).isEqualTo("zINSTREAM");
                        var input = new DataInputStream(socket.getInputStream());
                        int count = input.readInt(); assertThat(input.readNBytes(count)).isEqualTo("scan me".getBytes(StandardCharsets.US_ASCII));
                        assertThat(input.readInt()).isZero();
                        socket.getOutputStream().write((infected ? "stream: Eicar-Test-Signature FOUND\0" : "stream: OK\0").getBytes(StandardCharsets.US_ASCII));
                    }
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            Path file = Path.of("target/clam-stream-fixture.txt"); Files.createDirectories(file.getParent()); Files.writeString(file, "scan me");
            var scanner = new ClamAvScanner("127.0.0.1", server.getLocalPort(), Duration.ofSeconds(2), Duration.ofHours(72), Clock.systemUTC());
            assertThat(scanner.scan(file)).isEqualTo(infected ? MalwareScanner.Verdict.INFECTED : MalwareScanner.Verdict.CLEAN);
            serving.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void refusesUnavailableScannerInsteadOfDeclaringTheFileClean() throws Exception {
        int port; try (var server = new ServerSocket(0)) { port = server.getLocalPort(); }
        var scanner = new ClamAvScanner("127.0.0.1", port, Duration.ofMillis(100), Duration.ofHours(72), Clock.systemUTC());
        assertThatThrownBy(() -> scanner.scan(Path.of("target/missing.txt"))).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void staleDefinitionsFailClosedBeforeSendingResourceBytes() throws Exception {
        try (var server = new ServerSocket(0)) {
            var serving = CompletableFuture.runAsync(() -> {
                try (var socket = server.accept()) {
                    readCommand(socket.getInputStream());
                    socket.getOutputStream().write("ClamAV 1.5.2/28000/Mon Jan 1 00:00:00 2024\0".getBytes(StandardCharsets.US_ASCII));
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            var scanner = new ClamAvScanner("127.0.0.1", server.getLocalPort(), Duration.ofSeconds(1), Duration.ofHours(72), Clock.systemUTC());
            assertThatThrownBy(() -> scanner.scan(Path.of("target/missing.txt"))).isInstanceOf(java.io.IOException.class).hasMessageContaining("stale");
            serving.get(2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void blockedScannerWritesHaveABoundedDeadline() throws Exception {
        try (var server = new ServerSocket(0)) {
            var serving = CompletableFuture.runAsync(() -> {
                try {
                    try (var socket = server.accept()) {
                        readCommand(socket.getInputStream());
                        String date = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.US).withZone(ZoneOffset.UTC).format(Instant.now());
                        socket.getOutputStream().write(("ClamAV 1.5.2/28000/" + date + "\0").getBytes(StandardCharsets.US_ASCII));
                    }
                    try (var socket = server.accept()) {
                        socket.setReceiveBufferSize(1024);
                        Thread.sleep(2500); // Never consume the file stream.
                    }
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            Path file = Path.of("target/clam-blocked-fixture.bin"); Files.createDirectories(file.getParent()); Files.write(file, new byte[10 * 1024 * 1024]);
            var scanner = new ClamAvScanner("127.0.0.1", server.getLocalPort(), Duration.ofMillis(150), Duration.ofHours(72), Clock.systemUTC());
            long started = System.nanoTime();
            assertThatThrownBy(() -> scanner.scan(file)).isInstanceOf(java.io.IOException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
            serving.get(4, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private static String readCommand(java.io.InputStream input) throws Exception {
        var result = new java.io.ByteArrayOutputStream(); int value;
        while ((value = input.read()) > 0) result.write(value);
        return result.toString(StandardCharsets.US_ASCII);
    }
}
