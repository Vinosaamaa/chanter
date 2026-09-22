import java.io.InputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Fixed private routes inside the owning container. Credentials and bodies never enter argv or diagnostics. */
public final class Lifecycle {
    static final int PAGE_LIMIT = 256 * 1024;
    record Operation(String method, String path, int inputLimit) {}

    static Operation operation(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException();
        String base = "/api/v1/internal/lifecycle/";
        if (args[0].equals("export") && args.length == 3) {
            long after = revision(args[1]);
            String upper = args[2].equals("-") ? "" : "&through=" + revision(args[2]);
            return new Operation("GET", base + "journal?after=" + after + "&limit=500" + upper, 0);
        }
        if (args.length != 1) throw new IllegalArgumentException();
        return switch (args[0]) {
            case "checkpoint-get" -> new Operation("GET", base + "journal/checkpoint", 0);
            case "checkpoint-put" -> new Operation("POST", base + "journal/checkpoint", 2048);
            case "reapply" -> new Operation("POST", base + "journal/reapply", PAGE_LIMIT);
            case "receipt" -> new Operation("GET", base + "journal/reapply/receipt", 0);
            case "invalidate" -> new Operation("POST", base + "recovery/invalidate-sessions", 2048);
            default -> throw new IllegalArgumentException();
        };
    }

    static long revision(String value) {
        if (!value.matches("0|[1-9][0-9]{0,15}")) throw new IllegalArgumentException();
        long result = Long.parseLong(value);
        if (result > 9_007_199_254_740_991L) throw new IllegalArgumentException();
        return result;
    }

    static byte[] bounded(InputStream input, int maximum) throws Exception {
        byte[] value = input.readNBytes(maximum + 1);
        if (value.length > maximum) throw new IllegalArgumentException();
        return value;
    }

    public static void main(String[] args) {
        // Covers stdin, headers and a stalled streaming body. The host also imposes a process deadline.
        Thread.ofPlatform().daemon(true).start(() -> {
            try { Thread.sleep(20_000); } catch (InterruptedException ignored) { return; }
            System.exit(2);
        });
        try {
            var operation = operation(args);
            String token = System.getenv("CHANTER_INTERNAL_SERVICE_TOKEN");
            if (token == null || token.isBlank() || token.length() > 512 || token.contains("\r") || token.contains("\n"))
                throw new IllegalArgumentException();
            byte[] body = bounded(System.in, operation.inputLimit());
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080" + operation.path()))
                    .timeout(Duration.ofSeconds(15)).header("X-Internal-Service-Token", token)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Cache-Control", "no-store")
                    .method(operation.method(), body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector() {
                        public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                        public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) {}
                    }).build()) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var input = response.body()) {
                    if (response.statusCode() != 200 || !response.headers().firstValue("Content-Type").orElse("")
                            .matches("(?i)application/json(?:;.*)?")) throw new IllegalArgumentException();
                    byte[] result = bounded(input, PAGE_LIMIT);
                    System.out.write(result);
                }
            }
        } catch (Exception ignored) {
            System.err.println("Lifecycle helper failed");
            System.exit(1);
        }
    }
}
