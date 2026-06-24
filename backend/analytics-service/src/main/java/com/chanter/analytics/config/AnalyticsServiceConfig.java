package com.chanter.analytics.config;

import com.chanter.analytics.infra.HttpAgentServiceClient;
import com.chanter.analytics.infra.HttpCommunityServiceClient;
import com.chanter.analytics.infra.HttpMessageServiceClient;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        CommunityServiceClientProperties.class,
        MessageServiceClientProperties.class,
        AgentServiceClientProperties.class
})
public class AnalyticsServiceConfig {

    @Bean
    HttpClient analyticsHttpClient() {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    @Bean
    HttpCommunityServiceClient httpCommunityServiceClient(
            CommunityServiceClientProperties properties,
            HttpClient analyticsHttpClient
    ) {
        return new HttpCommunityServiceClient(properties, analyticsHttpClient);
    }

    @Bean
    HttpMessageServiceClient httpMessageServiceClient(
            MessageServiceClientProperties properties,
            HttpClient analyticsHttpClient
    ) {
        return new HttpMessageServiceClient(properties, analyticsHttpClient);
    }

    @Bean
    HttpAgentServiceClient httpAgentServiceClient(
            AgentServiceClientProperties properties,
            HttpClient analyticsHttpClient
    ) {
        return new HttpAgentServiceClient(properties, analyticsHttpClient);
    }
}
