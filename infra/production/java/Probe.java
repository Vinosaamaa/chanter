import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Runs without curl, a shell pipeline, credentials or Spring initialization. */
public final class Probe {
    public static void main(String[] args) {
        try {
            var request = HttpRequest.newBuilder(URI.create(args[0])).timeout(Duration.ofSeconds(5)).GET().build();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean up = response.statusCode() == 200 && (!args[0].contains("/actuator/")
                    // Deployment config hides components; accept only the compact root status.
                    || response.body().matches("\\s*\\{\\s*\"status\"\\s*:\\s*\"UP\"\\s*}\\s*"));
            if (!up) System.exit(1);
        } catch (Exception ignored) {
            System.exit(1);
        }
    }
}
