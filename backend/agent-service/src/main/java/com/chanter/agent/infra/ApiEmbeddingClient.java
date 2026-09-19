package com.chanter.agent.infra;

import com.chanter.agent.application.EmbeddingClient;
import com.chanter.agent.application.EmbeddingCodec;
import com.chanter.agent.domain.EmbeddingModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Explicit operator-configured embeddings API. No fallback or automatic provider selection. */
public final class ApiEmbeddingClient implements EmbeddingClient {
    private final RestClient client;
    private final EmbeddingModel metadata;
    private final String path;
    private final String apiKey;
    private final ObjectMapper mapper=new ObjectMapper();
    public ApiEmbeddingClient(String id, URI baseUri, String apiKey, String model, String revision, int dimensions) {
        boolean loopback=baseUri.getHost()!=null && java.util.Set.of("127.0.0.1","localhost","[::1]").contains(baseUri.getHost());
        if(baseUri.getHost()==null || baseUri.getUserInfo()!=null || baseUri.getQuery()!=null || baseUri.getFragment()!=null
                || !("https".equals(baseUri.getScheme()) || loopback && "http".equals(baseUri.getScheme()))) {
            throw new IllegalArgumentException("Embedding API requires HTTPS or explicit loopback HTTP");
        }
        metadata=new EmbeddingModel(id,"api",model,revision,dimensions);
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        client=RestClient.builder().baseUrl(baseUri.toString().replaceAll("/+$","")).requestFactory(factory).build();
        path=baseUri.getPath()==null || baseUri.getPath().equals("") || baseUri.getPath().equals("/") ? "/v1/embeddings" : "/embeddings";
        this.apiKey=apiKey;
    }
    @Override public String modelId(){return metadata.id();}
    @Override public int dimensions(){return metadata.dimensions();}
    @Override public EmbeddingModel metadata(){return metadata;}
    @Override public float[] embed(String text) {
        if(text==null || text.isBlank() || text.length()>16384) throw new IllegalArgumentException("Invalid bounded embedding input");
        try {
            var request=client.post().uri(path);
            if(apiKey!=null && !apiKey.isBlank()) request.header("Authorization","Bearer "+apiKey);
            return request.body(Map.of("model",metadata.model(),"input",text,"encoding_format","float"))
                    .exchange((sent,response)->{
                        if(!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Embedding API rejected request");
                        byte[] bytes=response.getBody().readNBytes(65537);
                        if(bytes.length>65536) throw new IllegalStateException("Embedding API response exceeded limit");
                        var root=mapper.readTree(bytes);
                        var data=root.path("data");
                        if(!metadata.model().equals(root.path("model").asText()) || !data.isArray() || data.size()!=1
                                || !data.get(0).path("index").isIntegralNumber() || !data.get(0).path("index").canConvertToInt()
                                || data.get(0).path("index").asInt()!=0) {
                            throw new IllegalStateException("Embedding API returned a different model or result identity");
                        }
                        var values=data.get(0).path("embedding");
                        if(!values.isArray() || values.size()!=dimensions()) throw new IllegalStateException("Embedding API dimensions do not match model");
                        float[] vector=new float[dimensions()];
                        for(int i=0;i<vector.length;i++) {
                            if(!values.get(i).isNumber()) throw new IllegalStateException("Embedding API returned nonnumeric coordinates");
                            vector[i]=(float)values.get(i).asDouble();
                            if(!Float.isFinite(vector[i])) throw new IllegalStateException("Embedding API returned nonfinite coordinates");
                        }
                        vector=EmbeddingCodec.l2Normalize(vector);
                        try { VectorValue.encode(vector,dimensions()); }
                        catch(IllegalArgumentException invalid) { throw new IllegalStateException("Embedding API returned invalid coordinates"); }
                        return vector;
                    });
        } catch(org.springframework.web.client.RestClientException unavailable) {
            throw new IllegalStateException("Embedding API is unavailable");
        }
    }
}
