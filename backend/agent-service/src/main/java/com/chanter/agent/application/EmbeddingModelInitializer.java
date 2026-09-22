package com.chanter.agent.application;

import com.chanter.common.recovery.OrdinaryOperation;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Model registration may create indexes; isolated recovery keeps the restored catalog unchanged. */
@Component
@OrdinaryOperation
public class EmbeddingModelInitializer {
    private final EmbeddingModelRouter models;

    public EmbeddingModelInitializer(EmbeddingModelRouter models) { this.models=models; }

    @PostConstruct void initialize() { models.initialize(); }
}
