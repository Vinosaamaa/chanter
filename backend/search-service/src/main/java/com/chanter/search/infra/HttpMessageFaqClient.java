package com.chanter.search.infra;

import com.chanter.search.application.MessageFaqClient;
import com.chanter.search.config.MessageServiceClientProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("!test")
public class HttpMessageFaqClient implements MessageFaqClient {

    private final RestClient restClient;

    public HttpMessageFaqClient(MessageServiceClientProperties properties) {
        this.restClient = DownstreamRestClientFactory.create(
                properties.baseUrl(),
                properties.connectTimeout(),
                properties.readTimeout()
        );
    }

    @Override
    public List<ApprovedFaqSummary> listApprovedFaqs(UUID courseId, UUID viewerUserId) {
        try {
            ApprovedFaqListResponse response = restClient.get()
                    .uri("/api/v1/courses/{courseId}/approved-faqs?viewerUserId={viewerUserId}", courseId, viewerUserId)
                    .retrieve()
                    .body(ApprovedFaqListResponse.class);

            if (response == null || response.approvedFaqs() == null) {
                return List.of();
            }

            return response.approvedFaqs().stream()
                    .map(faq -> new ApprovedFaqSummary(faq.id(), faq.courseId(), faq.question(), faq.answer()))
                    .toList();
        } catch (HttpClientErrorException.NotFound exception) {
            return List.of();
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
