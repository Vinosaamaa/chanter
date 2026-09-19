package com.chanter.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chanter.jwt.secret=chanter-test-jwt-secret-32bytes-min!!")
class AiModelRouteTest {
    @Autowired RouteLocator routes;
    @Test void modelCatalogUsesTheAgentRouteWithoutStealingOtherCourseRoutes() {
        assertThat(routeFor("/api/v1/course-channels/fixture/assistant-models")).isEqualTo("agent-service-support-question-answer");
        assertThat(routeFor("/api/v1/course-channels/fixture/assistant-models/other")).isNotEqualTo("agent-service-support-question-answer");
        assertThat(routeFor("/api/v1/native-companion/configuration")).isEqualTo("agent-service-support-question-answer");
        assertThat(routeFor("/api/v1/course-channels/fixture/support-questions/question/native-request")).isEqualTo("agent-service-support-question-answer");
        assertThat(routeFor("/api/v1/course-channels/fixture/support-questions/question/native-results/request")).isEqualTo("agent-service-support-question-answer");
        assertThat(routeFor("/api/v1/course-channels/fixture/support-questions")).isNotEqualTo("agent-service-support-question-answer");
    }
    private String routeFor(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        Route route = routes.getRoutes().filterWhen(candidate -> candidate.getPredicate().apply(exchange))
                .sort(Comparator.comparingInt(Route::getOrder)).next().block(Duration.ofSeconds(5));
        return route == null ? null : route.getId();
    }
}
