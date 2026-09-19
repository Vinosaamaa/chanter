package com.chanter.agent.infra;

import com.chanter.agent.application.ResourceIngestionJobs;
import com.chanter.agent.application.ResourceIngestionSource;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!test")
public class HttpResourceIngestionSource implements ResourceIngestionSource {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final RestClient client;
    private final String token;

    public HttpResourceIngestionSource(@Value("${chanter.media-service.base-url:http://localhost:8084}") String baseUrl,
            @Value("${chanter.internal-service-token}") String token) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.token = InternalServiceTokens.require(token);
    }

    @Override public byte[] download(ResourceIngestionJobs.Job job) {
        return client.get().uri(builder -> builder.path("/api/v1/internal/resource-ingestion/{id}/content")
                        .queryParam("courseId", job.courseId()).queryParam("sha256", job.sourceSha256()).build(job.resourceId()))
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token).exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Resource source is unavailable");
                    if (response.getHeaders().getContentLength() > MAX_BYTES) throw new IllegalStateException("Resource source exceeds limit");
                    byte[] bytes = response.getBody().readNBytes(MAX_BYTES + 1);
                    if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalStateException("Resource source size is invalid");
                    return bytes;
                });
    }
}
