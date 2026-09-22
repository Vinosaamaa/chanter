package com.chanter.agent.application;

import com.chanter.agent.domain.EmbeddingModel;
import com.chanter.agent.infra.EmbeddingProviders;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmbeddingStartupIsolationTest {
    @Test void recoveryRetainsRouterWithoutRegisteringModelsOrInitializingIndexes() {
        var client=mock(EmbeddingClient.class);
        var providers=mock(EmbeddingProviders.class);
        var versions=mock(EmbeddingVersionStore.class);
        when(providers.all()).thenReturn(List.of(client));
        when(providers.defaultClient()).thenReturn(client);
        when(versions.writableIds()).thenReturn(Set.of());
        new ApplicationContextRunner().withPropertyValues("chanter.recovery-mode=true")
                .withBean(EmbeddingProviders.class,()->providers)
                .withBean(EmbeddingVersionStore.class,()->versions)
                .withUserConfiguration(EmbeddingModelRouter.class,EmbeddingModelInitializer.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmbeddingModelRouter.class)
                            .doesNotHaveBean(EmbeddingModelInitializer.class);
                    verifyNoInteractions(versions);
                });
    }

    @Test void ordinaryStartupStillRegistersConfiguredModelsAndInitializesTheDefault() {
        var model=new EmbeddingModel("fixture","fixture","fixture","v1",8);
        var client=mock(EmbeddingClient.class);
        when(client.metadata()).thenReturn(model);
        var providers=mock(EmbeddingProviders.class);
        var versions=mock(EmbeddingVersionStore.class);
        when(providers.all()).thenReturn(List.of(client));
        when(providers.defaultClient()).thenReturn(client);
        when(versions.writableIds()).thenReturn(Set.of(model.id()));
        new ApplicationContextRunner()
                .withBean(EmbeddingProviders.class,()->providers)
                .withBean(EmbeddingVersionStore.class,()->versions)
                .withUserConfiguration(EmbeddingModelRouter.class,EmbeddingModelInitializer.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EmbeddingModelInitializer.class);
                    var order=inOrder(versions,providers);
                    order.verify(versions).register(model);
                    order.verify(versions).initializeDefault(model);
                    order.verify(versions).writableIds();
                    order.verify(providers).client(model.id());
                });
    }
}
