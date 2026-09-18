package com.chanter.community.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FreeBetaConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(BetaConfiguration.class);

    @Test
    void omittedConfigurationUsesBoundedFreeBeta() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            FreeBetaProperties policy = application.getBean(FreeBetaProperties.class);
            assertThat(policy.mode()).isEqualTo("free_beta");
            assertThat(policy.assistantRunLimit()).isEqualTo(1000);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "chanter.beta.mode=paid", "chanter.beta.mode=",
            "chanter.beta.assistant-run-limit=0", "chanter.beta.assistant-run-limit=1001"
    })
    void invalidOperatorPolicyPreventsStartup(String invalidProperty) {
        context.withPropertyValues(invalidProperty).run(application ->
                assertThat(application).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FreeBetaProperties.class)
    static class BetaConfiguration { }
}
