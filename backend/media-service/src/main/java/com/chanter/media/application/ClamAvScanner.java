package com.chanter.media.application;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClamAvScanner implements MalwareScanner {
    private static final java.util.concurrent.ScheduledExecutorService DEADLINES = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
            runnable -> { var thread = new Thread(runnable, "clamav-socket-deadlines"); thread.setDaemon(true); return thread; });
    private final String host;
    private final int port;
    private final Duration timeout;
    private final Duration maximumAge;
    private final Clock clock;
    public ClamAvScanner(@Value("${chanter.media.scanner.host:localhost}") String host,
            @Value("${chanter.media.scanner.port:3310}") int port,
            @Value("${chanter.media.scanner.timeout:20s}") Duration timeout,
            @Value("${chanter.media.scanner.maximum-definition-age:72h}") Duration maximumAge, Clock clock) {
        if (port < 1 || port > 65535 || timeout.toMillis() < 1 || timeout.compareTo(Duration.ofSeconds(30)) > 0
                || maximumAge.isNegative() || maximumAge.isZero() || maximumAge.compareTo(Duration.ofDays(7)) > 0) throw new IllegalArgumentException("Invalid scanner policy");
        this.host = host; this.port = port; this.timeout = timeout; this.maximumAge = maximumAge; this.clock = clock;
    }
    @Override public Verdict scan(Path file) throws IOException {
        try (var socket = connect()) {
            socket.getOutputStream().write("zVERSION\0".getBytes(StandardCharsets.US_ASCII));
            String[] version = response(socket).split("/", 3);
            if (version.length != 3) throw new IOException("Scanner definitions are unavailable");
            try {
                var updated = LocalDateTime.parse(version[2].strip().replaceAll("\\s+", " "), DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.US)).toInstant(ZoneOffset.UTC);
                if (updated.isBefore(clock.instant().minus(maximumAge)) || updated.isAfter(clock.instant().plusSeconds(300))) {
                    throw new IOException("Scanner definitions are stale");
                }
            } catch (java.time.format.DateTimeParseException exception) { throw new IOException("Scanner definitions are unavailable"); }
        }
        try (var socket = connect(); var source = Files.newInputStream(file)) {
            socket.getOutputStream().write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            var output = new DataOutputStream(socket.getOutputStream());
            byte[] buffer = new byte[8192]; int count; long size = 0;
            while ((count = source.read(buffer)) != -1) {
                if ((size += count) > 10L * 1024 * 1024) throw new IOException("Scanner stream limit exceeded");
                output.writeInt(count); output.write(buffer, 0, count);
            }
            output.writeInt(0); output.flush();
            String result = response(socket);
            if (result.equals("stream: OK")) return Verdict.CLEAN;
            if (result.startsWith("stream: ") && result.endsWith(" FOUND")) return Verdict.INFECTED;
            throw new IOException("Scanner did not verify the resource");
        }
    }
    private Socket connect() throws IOException {
        // SO_TIMEOUT covers reads only. Closing the socket also bounds a blocked INSTREAM write.
        Socket socket = new Socket() {
            private final java.util.concurrent.ScheduledFuture<?> deadline = DEADLINES.schedule(() -> {
                try { close(); } catch (IOException ignored) { }
            }, timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            @Override public void close() throws IOException { if (deadline != null) deadline.cancel(false); super.close(); }
        };
        try {
            socket.connect(new InetSocketAddress(host, port), Math.min(3000, Math.toIntExact(timeout.toMillis())));
            socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));
            return socket;
        } catch (IOException exception) { socket.close(); throw new IOException("Scanner is unavailable"); }
    }
    private static String response(Socket socket) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream(); int value;
        while ((value = socket.getInputStream().read()) != 0) {
            if (value == -1 || bytes.size() >= 4096) throw new IOException("Invalid scanner response");
            bytes.write(value);
        }
        return bytes.toString(StandardCharsets.US_ASCII).strip();
    }
}
