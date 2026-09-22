package com.chanter.common.telemetry;

import com.chanter.common.recovery.OrdinaryOperation;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@OrdinaryOperation
@ConditionalOnClass(LoggerContext.class)
@ConditionalOnProperty(name = "chanter.errors.enabled", havingValue = "true")
public class PrivateErrorsConfiguration {
    @Bean(initMethod = "start", destroyMethod = "close")
    PrivateErrorReporter privateErrorReporter(Environment settings) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context))
            throw new IllegalStateException("Private errors require the configured application logger");
        return new PrivateErrorReporter(PrivateErrorReporter.options(settings.getProperty("chanter.errors.dsn")), context,
                settings.getProperty("spring.application.name", "unknown-service"), settings.getProperty("CHANTER_RELEASE", ""),
                settings.getProperty("CHANTER_ENVIRONMENT", "development"));
    }
}
