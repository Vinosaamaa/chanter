package com.chanter.agent.infra;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(named="VECTOR_INTEGRATION",matches="true")
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5547/vector_test?currentSchema=vector_fixture",
    "spring.datasource.username=vector_test", "spring.datasource.password=vector-test-only-password",
    "spring.flyway.schemas=vector_fixture", "spring.flyway.default-schema=vector_fixture"
})
class PostgresScopedVectorSearchTest extends ScopedVectorSearchTest {}
