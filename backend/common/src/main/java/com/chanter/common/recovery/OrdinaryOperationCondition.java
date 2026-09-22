package com.chanter.common.recovery;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class OrdinaryOperationCondition implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return switch (context.getEnvironment().getProperty("chanter.recovery-mode", "false")) {
            case "false" -> true;
            case "true" -> false;
            default -> throw new IllegalStateException("Invalid recovery mode; refusing ordinary worker startup");
        };
    }
}
