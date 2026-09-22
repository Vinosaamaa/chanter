import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Hosted-only transport fixture. This does not implement or prove any source deletion effect. */
public final class LifecycleHttpFixture {
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("CI"))) throw new IllegalStateException("Hosted fixture only");
        var mode = new AtomicReference<>("ok");
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.setExecutor(executor);
        server.createContext("/api/v1/internal/lifecycle/journal/reapply/receipt", exchange -> {
            try (exchange) {
                if (!"fixture-private-token".equals(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"))) {
                    exchange.sendResponseHeaders(403, -1); return;
                }
                if (mode.get().equals("redirect")) {
                    exchange.getResponseHeaders().set("Location", "http://127.0.0.1:8080/forbidden");
                    exchange.sendResponseHeaders(302, -1); return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] body = (mode.get().equals("large") ? "x".repeat(256 * 1024 + 1) : "{\"fixture\":true}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                if (mode.get().equals("stall")) {
                    try { Thread.sleep(25_000); } catch (InterruptedException ignored) { return; }
                }
                exchange.getResponseBody().write(body);
            } catch (java.io.IOException expectedAfterCancellation) {}
        });
        server.start();
        try {
            for (String test : new String[]{"ok", "redirect", "large", "stall"}) {
                mode.set(test);
                var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Xmx32m", "-XX:+UseSerialGC", "-cp", System.getProperty("java.class.path"), "Lifecycle", "receipt");
                command.environment().put("CHANTER_INTERNAL_SERVICE_TOKEN", "fixture-private-token");
                var child = command.start(); child.getOutputStream().close();
                if (!child.waitFor(24, TimeUnit.SECONDS)) { child.destroyForcibly(); throw new AssertionError("Unbounded lifecycle helper"); }
                String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String error = new String(child.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                if (test.equals("ok")) {
                    if (child.exitValue() != 0 || !output.equals("{\"fixture\":true}")) throw new AssertionError("Transport success missing");
                } else if (child.exitValue() == 0 || !output.isEmpty()) throw new AssertionError("Transport failure leaked success");
                if (error.contains("fixture-private-token") || error.contains("xxx")) throw new AssertionError("Private diagnostic leak");
            }
        } finally { server.stop(0); executor.shutdownNow(); }
        System.out.println("Hosted lifecycle transport, size, redirect and streaming deadline cases passed");
    }
}
