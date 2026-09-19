package com.chanter.agent.infra;

import com.chanter.agent.application.EmbeddingClient;
import com.chanter.agent.application.HashingEmbeddingClient;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Provider definitions come from operator configuration; management requests only select registered IDs. */
public final class EmbeddingProviders implements AutoCloseable {
    private final Map<String,EmbeddingClient> clients;
    private final String defaultId;
    public EmbeddingProviders(List<EmbeddingClient> configured, String defaultId) {
        var available=new LinkedHashMap<String,EmbeddingClient>();
        for(var client:configured) {
            if(available.putIfAbsent(client.modelId(),client)!=null) throw new IllegalArgumentException("Duplicate embedding model ID");
        }
        clients=Map.copyOf(available); this.defaultId=defaultId;
        client(defaultId);
    }
    public List<EmbeddingClient> all(){return List.copyOf(clients.values());}
    public EmbeddingClient defaultClient(){return client(defaultId);}
    public EmbeddingClient client(String id) {
        var client=id==null?null:clients.get(id);
        if(client==null) throw new IllegalStateException("Required embedding model is not configured");
        return client;
    }
    @Override public void close() throws Exception {
        for(var client:clients.values()) if(client instanceof AutoCloseable resource) resource.close();
    }
    public record ApiDefinition(String id,String baseUrl,String apiKey,String model,String revision,int dimensions) {}

    @Configuration
    public static class Config {
        @Bean(destroyMethod="close")
        EmbeddingProviders configuredEmbeddingProviders(Environment environment,ObjectProvider<HashingEmbeddingClient> testClient) {
            if(environment.acceptsProfiles(Profiles.of("test"))) {
                var fixture=testClient.getObject();
                return new EmbeddingProviders(List.of(fixture),fixture.modelId());
            }
            String provider=environment.getProperty("chanter.embeddings.provider","onnx");
            if(!List.of("onnx","api").contains(provider)) throw new IllegalStateException("Production requires an explicit semantic embedding provider");
            var configured=new java.util.ArrayList<EmbeddingClient>();
            try {
            String defaultId=environment.getProperty("chanter.embeddings.default-model");
            if(provider.equals("onnx")) {
                var local=new OnnxEmbeddingClient(Path.of(environment.getProperty("chanter.embeddings.model-directory",".cache/models/minilm")));
                configured.add(local);
                if(defaultId==null || defaultId.isBlank()) defaultId=local.modelId();
            }
            var definitions=Binder.get(environment).bind("chanter.embeddings.api-models",Bindable.listOf(ApiDefinition.class)).orElse(List.of());
            if(definitions.size()>2) throw new IllegalArgumentException("At most two API embedding models may be configured");
            for(var definition:definitions) configured.add(new ApiEmbeddingClient(definition.id(),URI.create(definition.baseUrl()),
                    definition.apiKey(),definition.model(),definition.revision(),definition.dimensions()));
            return new EmbeddingProviders(configured,defaultId);
            } catch(RuntimeException failure) {
                for(var client:configured) if(client instanceof AutoCloseable resource) {
                    try { resource.close(); } catch(Exception closeFailure) { failure.addSuppressed(closeFailure); }
                }
                throw failure;
            }
        }
    }
}
