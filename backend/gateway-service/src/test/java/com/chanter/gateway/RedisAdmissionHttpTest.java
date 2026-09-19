package com.chanter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanter.common.auth.JwtTokenService;
import com.chanter.gateway.security.RedisRequestBudgetStore;
import com.chanter.gateway.security.RequestBudgetStore;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;

/** Hosted CI supplies an isolated real Redis. Local hermetic runs explicitly skip this external dependency. */
@EnabledIfEnvironmentVariable(named = "CHANTER_TEST_REDIS_PORT", matches = "[0-9]+")
class RedisAdmissionHttpTest {
    private static final String JWT_SECRET = "redis-admission-http-fixture-32bytes-minimum";
    private static final String KEY_SECRET = UUID.randomUUID().toString();
    private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static HttpServer upstream;
    private static ConfigurableApplicationContext first;
    private static ConfigurableApplicationContext second;

    @BeforeAll static void start() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        upstream.start();
        first = gateway(Integer.parseInt(System.getenv("CHANTER_TEST_REDIS_PORT")));
        second = gateway(Integer.parseInt(System.getenv("CHANTER_TEST_REDIS_PORT")));
    }

    @AfterAll static void stop() {
        if (first != null) first.close();
        if (second != null) second.close();
        if (upstream != null) upstream.stop(0);
    }

    @Test void twoActualGatewaysShareRegistrationBudgetDespiteSpoofedAddresses() {
        var replies = IntStream.range(0, 16).mapToObj(index -> http.sendAsync(request(index % 2 == 0 ? first : second,
                "/api/v1/auth/register").header("X-Forwarded-For", "198.51.100." + index).build(), HttpResponse.BodyHandlers.ofString())).toList();
        var results = replies.stream().map(CompletableFuture::join).toList();
        assertThat(results.stream().filter(reply -> reply.statusCode() == 204).count()).isEqualTo(12);
        assertThat(results.stream().filter(reply -> reply.statusCode() == 429).count()).isEqualTo(4);
        results.stream().filter(reply -> reply.statusCode() == 429).forEach(reply -> {
            assertThat(reply.headers().firstValue("Retry-After")).isPresent();
            assertThat(reply.body()).contains("RATE_LIMITED").doesNotContain("198.51.100", KEY_SECRET);
        });
    }

    @Test void tenantRotationAndMultipleGatewaysCannotResetTheVerifiedUserAiBudget() {
        String token = new JwtTokenService(JWT_SECRET, 900).createAccessToken(UUID.randomUUID());
        var replies = IntStream.range(0, 25).mapToObj(index -> http.sendAsync(request(index % 2 == 0 ? first : second,
                "/api/v1/study-servers/" + UUID.randomUUID() + "/study-assistant")
                .header("Authorization", "Bearer " + token).build(), HttpResponse.BodyHandlers.ofString())).toList();
        var results = replies.stream().map(CompletableFuture::join).toList();
        assertThat(results.stream().filter(reply -> reply.statusCode() == 204).count()).isEqualTo(20);
        assertThat(results.stream().filter(reply -> reply.statusCode() == 429).count()).isEqualTo(5);
    }

    @Test void realRedisAtomicCountersExpireAndRejectAConcurrentBurst() throws Exception {
        var redis = first.getBean(ReactiveStringRedisTemplate.class);
        var left = new RedisRequestBudgetStore(redis, 1000);
        var right = new RedisRequestBudgetStore(second.getBean(ReactiveStringRedisTemplate.class), 1000);
        String key = "chanter:admission:test:" + UUID.randomUUID();
        var budgets = List.of(new RequestBudgetStore.Budget(key, 3));
        var replies = Flux.range(0, 20).flatMap(index -> (index % 2 == 0 ? left : right).acquire(budgets)).collectList().block();
        assertThat(replies).filteredOn(value -> value == 0).hasSize(3);
        assertThat(redis.getExpire(key).block()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(1));
        Thread.sleep(1100);
        assertThat(left.acquire(budgets).block()).isZero();
    }

    @Test void actualRedisConnectionFailureClosesSignupWhileLogoutAndLivenessRemainReachable() throws Exception {
        int unavailablePort;
        try (var socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) { unavailablePort = socket.getLocalPort(); }
        try (var unavailable = gateway(unavailablePort)) {
            var signup = http.send(request(unavailable, "/api/v1/auth/register").build(), HttpResponse.BodyHandlers.ofString());
            assertThat(signup.statusCode()).isEqualTo(503);
            assertThat(signup.body()).contains("ADMISSION_UNAVAILABLE").doesNotContain("Redis", "localhost");
            assertThat(http.send(request(unavailable, "/api/v1/auth/logout").build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(204);
            var health = HttpRequest.newBuilder(uri(unavailable, "/actuator/health/liveness")).GET().build();
            assertThat(http.send(health, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        }
    }

    private static ConfigurableApplicationContext gateway(int redisPort) {
        String backend = "http://127.0.0.1:" + upstream.getAddress().getPort();
        return SpringApplication.run(GatewayServiceApplication.class, "--server.port=0", "--spring.main.banner-mode=off",
                "--chanter.jwt.secret=" + JWT_SECRET, "--chanter.edge.limits-enabled=true", "--chanter.edge.key-secret=" + KEY_SECRET,
                "--spring.data.redis.host=127.0.0.1", "--spring.data.redis.port=" + redisPort,
                "--spring.data.redis.password=", "--AUTH_SERVICE_URL=" + backend, "--AGENT_SERVICE_URL=" + backend,
                "--management.endpoint.health.probes.enabled=true", "--logging.level.root=WARN");
    }

    private static HttpRequest.Builder request(ConfigurableApplicationContext context, String path) {
        return HttpRequest.newBuilder(uri(context, path)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
    }

    private static URI uri(ConfigurableApplicationContext context, String path) {
        return URI.create("http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port") + path);
    }
}
