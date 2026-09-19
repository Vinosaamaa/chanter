package com.chanter.agent.application;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(named="VECTOR_INTEGRATION",matches="true")
@org.springframework.context.annotation.Import(EmbeddingRebuildJobsTest.Models.class)
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5547/vector_test?currentSchema=rebuild_fixture",
    "spring.datasource.username=vector_test", "spring.datasource.password=vector-test-only-password",
    "spring.flyway.schemas=rebuild_fixture", "spring.flyway.default-schema=rebuild_fixture"
})
class PostgresEmbeddingRebuildJobsTest extends EmbeddingRebuildJobsTest {}
