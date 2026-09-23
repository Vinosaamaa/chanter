import com.sun.net.httpserver.HttpServer;
import com.chanter.common.auth.AuthHeaders;
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
                if (!"fixture-private-token".equals(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN))) {
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
        String target = "22222222-2222-4222-8222-222222222222";
        String privateBody = "{\"private\":\"scope-body-canary\"}";
        var expectedPath = new AtomicReference<String>();
        server.createContext("/api/v1/internal/lifecycle/deleted-study-servers/", exchange -> {
            try (exchange) {
                String body = new String(exchange.getRequestBody().readNBytes(32769), StandardCharsets.UTF_8);
                boolean read = expectedPath.get().endsWith("/scope");
                boolean valid = "fixture-private-token".equals(exchange.getRequestHeaders().getFirst(AuthHeaders.INTERNAL_SERVICE_TOKEN))
                        && exchange.getRequestURI().getPath().equals(expectedPath.get())
                        && exchange.getRequestMethod().equals(read ? "GET" : "POST")
                        && body.equals(read ? "" : privateBody);
                if (read) valid &= exchange.getRequestURI().getQuery().equals("revision=1&eventId=11111111-1111-4111-8111-111111111111&digest="
                        + "a".repeat(64) + "&kind=COURSE&after=00000000-0000-0000-0000-000000000000&limit=256");
                byte[] result = "{\"fixture\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(valid ? 200 : 400, result.length); exchange.getResponseBody().write(result);
            }
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
            for (String[] route : new String[][]{{"scope-read", "/scope"}, {"scope-import", "/scope/import"},
                    {"scope-derive", "/scope/recovery/derive"}, {"scope-recovery-read", "/scope/recovery/page"},
                    {"scope-recovery-import", "/scope/recovery/import"}}) {
                expectedPath.set("/api/v1/internal/lifecycle/deleted-study-servers/" + target + route[1]);
                var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Xmx32m", "-XX:+UseSerialGC", "-cp", System.getProperty("java.class.path"), "Lifecycle", route[0]);
                command.environment().put("CHANTER_INTERNAL_SERVICE_TOKEN", "fixture-private-token");
                var child = command.start();
                String input = route[0].equals("scope-read") ? target + "\n1\n11111111-1111-4111-8111-111111111111\n"
                        + "a".repeat(64) + "\nCOURSE\n00000000-0000-0000-0000-000000000000\n" : target + "\n" + privateBody;
                try (var stdin = child.getOutputStream()) { stdin.write(input.getBytes(StandardCharsets.UTF_8)); }
                if (!child.waitFor(24, TimeUnit.SECONDS)) { child.destroyForcibly(); throw new AssertionError("Unbounded scope helper"); }
                if (child.exitValue() != 0 || !new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8).equals("{\"fixture\":true}"))
                    throw new AssertionError("Scope transport missing");
                String error = new String(child.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                if (error.contains("scope-body-canary") || error.contains("fixture-private-token")) throw new AssertionError("Scope diagnostic leak");
            }
        } finally { server.stop(0); executor.shutdownNow(); }
        System.out.println("Hosted lifecycle transport, size, redirect and streaming deadline cases passed");
    }
}
