package com.chanter.agent.infra;

import com.chanter.agent.application.ApprovedFaqClient;
import com.chanter.common.auth.AuthHeaders;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpApprovedFaqClient implements ApprovedFaqClient {

    private final RestClient restClient;

    public HttpApprovedFaqClient(
            @Value("${chanter.message-service.base-url:http://localhost:8083}") String messageServiceBaseUrl,
            @Value("${chanter.message-service.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${chanter.message-service.read-timeout-seconds:10}") int readTimeoutSeconds
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(messageServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<ApprovedFaqSummary> listApprovedFaqs(UUID courseId, UUID viewerUserId) {
        try {
            ApprovedFaqListResponse response = restClient.get()
                    .uri("/api/v1/courses/{courseId}/approved-faqs", courseId)
                    .header(AuthHeaders.USER_ID, viewerUserId.toString())
                    .retrieve()
                    .body(ApprovedFaqListResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service returned empty approved FAQs");
            }

            List<ApprovedFaqResponse> approvedFaqs = response.approvedFaqs() != null
                    ? response.approvedFaqs()
                    : List.of();

            return approvedFaqs.stream()
                    .map(faq -> new ApprovedFaqSummary(faq.id(), faq.courseId(), faq.question(), faq.answer()))
                    .toList();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found", exception);
        } catch (HttpClientErrorException.Forbidden exception) {
            return List.of();
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Message Service rejected the approved FAQ request",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Message Service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Message Service", exception);
        }
    }

    private record ApprovedFaqListResponse(List<ApprovedFaqResponse> approvedFaqs) {
    }

    private record ApprovedFaqResponse(UUID id, UUID courseId, String question, String answer) {
    }
}
