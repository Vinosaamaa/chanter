package com.chanter.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chanter.agent.application.LlmModelCatalog;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class LlmModelCatalogTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void disabledPopulatedCatalogCannotSelectOrCallAnyProvider() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        try {
            context.withPropertyValues("chanter.llm.enabled=false", "chanter.llm.default-model-id=local",
                            "chanter.llm.models.local.provider=ollama", "chanter.llm.models.local.model=fixture-local",
                            "chanter.llm.models.local.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                            "chanter.llm.models.hosted.provider=anthropic", "chanter.llm.models.hosted.model=claude-fixture",
                            "chanter.llm.models.hosted.api-key=fixture-secret")
                    .run(app -> {
                        assertThat(app).hasNotFailed();
                        var catalog = app.getBean(LlmModelCatalog.class);
                        assertThat(catalog.defaultModelId()).isEqualTo("source-only");
                        assertThat(catalog.modelsFor(UUID.randomUUID())).extracting(LlmModelCatalog.ModelView::id).containsExactly("source-only");
                        assertThat(catalog.select(null, UUID.randomUUID())).isEqualTo("source-only");
                        assertThatThrownBy(() -> catalog.select("local", UUID.randomUUID())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                        assertThatThrownBy(() -> catalog.definition("hosted")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                        for (String selection : java.util.List.of("local", "hosted")) {
                            var client = catalog.client(selection);
                            assertThat(client.isEnabled()).isFalse();
                            assertThat(client.ping()).isFalse();
                            assertThatThrownBy(() -> client.complete(new com.chanter.agent.application.LlmChatClient.LlmChatRequest("s", "u")))
                                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                        }
                        assertThat(calls).hasValue(0);
                    });
        } finally { server.stop(0); }
    }

    @Test
    void unconfiguredDefaultUsesOnlyFreeSources() {
        context.run(app -> {
            assertThat(app).hasNotFailed();
            var catalog = app.getBean(LlmModelCatalog.class);
            assertThat(catalog.defaultModelId()).isEqualTo("source-only");
            assertThat(catalog.modelsFor(UUID.randomUUID())).extracting(LlmModelCatalog.ModelView::id)
                    .containsExactly("source-only");
        });
    }

    @Test
    void rejectsAnOllamaModelAssignedToOpenAiAtStartup() {
        context.withPropertyValues("chanter.llm.models.hosted.provider=openai",
                        "chanter.llm.models.hosted.model=llama3.2",
                        "chanter.llm.models.hosted.api-key=fixture-key")
                .run(app -> assertThat(app).hasFailed());
    }

    @Test
    void modelSelectionRequiresExplicitCourseApprovalAndNeverExposesCredentials() {
        UUID allowed = UUID.randomUUID();
        context.withPropertyValues("chanter.llm.enabled=true", "chanter.llm.models.claude.provider=anthropic",
                        "chanter.llm.models.claude.model=claude-fixture",
                        "chanter.llm.models.claude.api-key=fixture-secret",
                        "chanter.llm.models.claude.allowed-course-ids[0]=" + allowed)
                .run(app -> {
                    assertThat(app).hasNotFailed();
                    var catalog = app.getBean(LlmModelCatalog.class);
                    assertThat(catalog.modelsFor(UUID.randomUUID())).hasSize(1);
                    assertThat(catalog.modelsFor(allowed)).extracting(LlmModelCatalog.ModelView::provider)
                            .containsExactly("none", "anthropic");
                    assertThat(catalog.modelsFor(allowed).toString()).doesNotContain("fixture-secret", "api.anthropic.com");
                });
    }

    @Test
    void rejectsCloudModelsInTheLocalProviderAndInvalidDefaults() {
        context.withPropertyValues("chanter.llm.models.local.provider=ollama",
                        "chanter.llm.models.local.model=fixture-cloud")
                .run(app -> assertThat(app).hasFailed());
        context.withPropertyValues("chanter.llm.default-model-id=missing")
                .run(app -> assertThat(app).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LlmProperties.class)
    static class Config {
        @Bean LlmModelCatalog catalog(LlmProperties properties) { return new LlmModelCatalog(properties); }
    }
}
