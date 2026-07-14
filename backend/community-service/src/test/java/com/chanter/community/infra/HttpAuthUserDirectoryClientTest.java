package com.chanter.community.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpAuthUserDirectoryClientTest {

    private static final String INTERNAL_SERVICE_TOKEN = "test-internal-service-token-for-community";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chunksLargeProfileQueriesToAuthBoundary() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://auth.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        AtomicInteger requestedUserCount = new AtomicInteger();
        server.expect(twice(), requestTo("http://auth.test/internal/v1/users/profiles/query"))
                .andExpect(method(POST))
                .andExpect(header(AuthHeaders.INTERNAL_SERVICE_TOKEN, INTERNAL_SERVICE_TOKEN))
                .andExpect(request -> {
                    int batchSize = objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString())
                            .get("userIds")
                            .size();
                    assertThat(batchSize).isBetween(1, 100);
                    requestedUserCount.addAndGet(batchSize);
                })
                .andRespond(withSuccess("{\"profiles\":[]}", MediaType.APPLICATION_JSON));
        HttpAuthUserDirectoryClient client = new HttpAuthUserDirectoryClient(
                restClientBuilder.build(),
                INTERNAL_SERVICE_TOKEN
        );
        List<UUID> userIds = java.util.stream.IntStream.range(0, 101)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();

        assertThat(client.findByIds(userIds)).isEmpty();

        server.verify();
        assertThat(requestedUserCount.get()).isEqualTo(101);
    }

    @Test
    void productionClientCanBeCreatedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    com.chanter.community.config.AuthServiceClientProperties.class,
                    () -> new com.chanter.community.config.AuthServiceClientProperties(
                            "http://auth.test",
                            1,
                            1,
                            INTERNAL_SERVICE_TOKEN
                    )
            );
            context.register(HttpAuthUserDirectoryClient.class);

            context.refresh();

            assertThat(context.getBean(HttpAuthUserDirectoryClient.class)).isNotNull();
        }
    }
}
