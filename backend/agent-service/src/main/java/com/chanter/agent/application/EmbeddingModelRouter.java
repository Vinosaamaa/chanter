package com.chanter.agent.application;

import com.chanter.agent.domain.EmbeddingModel;
import com.chanter.agent.infra.EmbeddingProviders;
import java.util.List;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Pins a single coordinate space for each query/preparation, with no fallback on provider failure. */
@Component @Primary @DependsOnDatabaseInitialization
public class EmbeddingModelRouter implements EmbeddingClient {
    private final EmbeddingProviders providers;
    private final EmbeddingVersionStore versions;
    public EmbeddingModelRouter(EmbeddingProviders providers,EmbeddingVersionStore versions) {
        this.providers=providers;this.versions=versions;
    }
    void initialize() {
        for(var client:providers.all()) versions.register(client.metadata());
        versions.initializeDefault(providers.defaultClient().metadata());
        for(String id:versions.writableIds()) providers.client(id);
    }
    public EmbeddingClient pinned(){return providers.client(versions.active().id());}
    public EmbeddingClient client(String id){return providers.client(id);}
    public List<EmbeddingClient> writers(){return versions.writableIds().stream().sorted().map(providers::client).toList();}
    @Override public String modelId(){return pinned().modelId();}
    @Override public int dimensions(){return pinned().dimensions();}
    @Override public EmbeddingModel metadata(){return pinned().metadata();}
    @Override public float[] embed(String text){return pinned().embed(text);}
}
