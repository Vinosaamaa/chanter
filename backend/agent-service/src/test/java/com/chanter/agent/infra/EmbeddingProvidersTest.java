package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.chanter.agent.application.HashingEmbeddingClient;
import com.chanter.agent.domain.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

class EmbeddingProvidersTest {
    @Test void productionRejectsHashingAndMissingSemanticConfiguration() {
        var config=new EmbeddingProviders.Config();
        @SuppressWarnings("unchecked") ObjectProvider<HashingEmbeddingClient> fixture=mock(ObjectProvider.class);
        assertThatThrownBy(()->config.configuredEmbeddingProviders(new MockEnvironment()
                .withProperty("chanter.embeddings.provider","hashing"),fixture)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->config.configuredEmbeddingProviders(new MockEnvironment()
                .withProperty("chanter.embeddings.provider","api"),fixture)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(fixture);
    }
    @Test void catalogLengthsAndUnsafeIndexIdentifiersAreRejectedBeforePersistence() {
        assertThatThrownBy(()->new EmbeddingModel("bad'identifier","api","model","revision",384)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EmbeddingModel("id","a".repeat(33),"model","revision",384)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EmbeddingModel("id","api","m".repeat(201),"revision",384)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EmbeddingModel("id","api","model","r".repeat(161),384)).isInstanceOf(IllegalArgumentException.class);
    }
}
