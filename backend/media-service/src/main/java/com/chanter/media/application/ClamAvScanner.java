package com.chanter.media.application;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
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
    private final Path socketPath;
    private final boolean tcpDevelopment;
    private final int port;
    private final Duration timeout;
    private final Duration maximumAge;
    private final Clock clock;
    public ClamAvScanner(@Value("${chanter.media.scanner.socket-path:/run/clamav/clamd.sock}") String socketPath,
            @Value("${chanter.media.scanner.tcp-development:false}") boolean tcpDevelopment,
            @Value("${chanter.media.scanner.host:127.0.0.1}") String host,
            @Value("${chanter.media.scanner.port:3310}") int port,
            @Value("${chanter.media.scanner.timeout:20s}") Duration timeout,
            @Value("${chanter.media.scanner.maximum-definition-age:72h}") Duration maximumAge, Clock clock) {
        if (port < 1 || port > 65535 || timeout.toMillis() < 1 || timeout.compareTo(Duration.ofSeconds(30)) > 0
                || maximumAge.isNegative() || maximumAge.isZero() || maximumAge.compareTo(Duration.ofDays(7)) > 0) throw new IllegalArgumentException("Invalid scanner policy");
        if (tcpDevelopment && !java.util.Set.of("127.0.0.1", "::1", "localhost").contains(host)) {
            throw new IllegalArgumentException("Development scanner TCP must use a literal loopback host");
        }
        this.socketPath = tcpDevelopment ? null : Path.of(socketPath);
        if (!tcpDevelopment && !this.socketPath.isAbsolute()) throw new IllegalArgumentException("Scanner socket path must be absolute");
        this.tcpDevelopment = tcpDevelopment;
        this.host = host; this.port = port; this.timeout = timeout; this.maximumAge = maximumAge; this.clock = clock;
    }
    @Override public Verdict scan(Path file) throws IOException {
        try (var socket = connect()) {
            socket.output().write("zVERSION\0".getBytes(StandardCharsets.US_ASCII));
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
            socket.output().write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            var output = new DataOutputStream(socket.output());
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
    private Connection connect() throws IOException {
        SocketChannel channel;
        try { channel = SocketChannel.open(tcpDevelopment
                ? (host.equals("::1") ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET) : StandardProtocolFamily.UNIX); }
        catch (UnsupportedOperationException unavailable) { throw new IOException("Local scanner transport is unavailable"); }
        // Closing the channel bounds connection, blocked writes and reads on both supported transports.
        var deadline = DEADLINES.schedule(() -> {
            try { channel.close(); } catch (IOException ignored) { }
        }, timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        var connection = new Connection(channel, deadline);
        try {
            if (tcpDevelopment) {
                String loopback = host.equals("localhost") ? "127.0.0.1" : host;
                channel.connect(new InetSocketAddress(loopback, port));
            } else channel.connect(UnixDomainSocketAddress.of(socketPath));
            return connection;
        } catch (IOException | RuntimeException exception) { connection.close(); throw new IOException("Scanner is unavailable"); }
    }
    private record Connection(SocketChannel channel, java.util.concurrent.ScheduledFuture<?> deadline) implements AutoCloseable {
        java.io.InputStream input() { return Channels.newInputStream(channel); }
        java.io.OutputStream output() { return Channels.newOutputStream(channel); }
        @Override public void close() throws IOException { deadline.cancel(false); channel.close(); }
    }
    private static String response(Connection socket) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream(); int value;
        var input = socket.input();
        while ((value = input.read()) != 0) {
            if (value == -1 || bytes.size() >= 4096) throw new IOException("Invalid scanner response");
            bytes.write(value);
        }
        return bytes.toString(StandardCharsets.US_ASCII).strip();
    }
}
