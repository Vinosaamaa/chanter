package com.chanter.search.infra;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.search.application.SearchSourceClient;
import com.chanter.search.config.CommunityServiceClientProperties;
import com.chanter.search.config.MessageServiceClientProperties;
import com.chanter.search.domain.SearchHit;
import com.chanter.search.domain.SearchDocumentType;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpSearchSourceClient implements SearchSourceClient {
    private final RestClient community;
    private final RestClient message;
    private final String token;
    public HttpSearchSourceClient(CommunityServiceClientProperties community, MessageServiceClientProperties message,
            @Value("${chanter.internal-service-token}") String token) {
        this.community = DownstreamRestClientFactory.create(community.baseUrl(), community.connectTimeout(), community.readTimeout());
        this.message = DownstreamRestClientFactory.create(message.baseUrl(), message.connectTimeout(), message.readTimeout());
        this.token = InternalServiceTokens.require(token);
    }
    @Override public Optional<SearchHit> currentVisibleHit(SearchHit hit, UUID server, UUID viewer) {
        String path = switch (hit.documentType()) {
            case EVENT -> "/api/v1/study-servers/" + server + "/events/" + hit.sourceId();
            case ANNOUNCEMENT -> "/api/v1/study-servers/" + server + "/announcements/" + hit.sourceId();
            case MESSAGE -> hit.channelId() == null || hit.channelScope() == null || !java.util.Set.of("COURSE", "STUDY_SERVER").contains(hit.channelScope()) ? null
                    : "/api/v1/" + (hit.channelScope().equals("COURSE") ? "course-channels/" : "study-server-channels/")
                        + hit.channelId() + "/messages/" + hit.sourceId();
            default -> null;
        };
        if (path == null) return Optional.empty();
        try {
            Source source = (hit.documentType() == SearchDocumentType.MESSAGE ? message : community).get().uri(path)
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token).header(AuthHeaders.USER_ID, viewer.toString())
                    .retrieve().body(Source.class);
            if (source == null || !hit.sourceId().equals(source.id())
                    || "CANCELLED".equals(source.status()) || "ARCHIVED".equals(source.status())) return Optional.empty();
            if (hit.documentType() == SearchDocumentType.EVENT
                    && !Objects.equals(source.courseId(), hit.courseId())) return Optional.empty();
            String title = hit.documentType() == SearchDocumentType.MESSAGE ? "Channel message" : Objects.toString(source.title(), "");
            String body = Objects.toString(hit.documentType() == SearchDocumentType.EVENT ? source.description() : source.body(), "");
            String href = hit.href();
            if (hit.documentType() == SearchDocumentType.MESSAGE) href = hit.courseId() == null
                    ? "/app/servers/" + server + "/community/lounge?channel=" + hit.channelId()
                    : "/app/servers/" + server + "/courses/" + hit.courseId() + "/chat?channel=" + hit.channelId();
            return Optional.of(new SearchHit(hit.documentType(), hit.courseId(), hit.courseTitle(), hit.sourceId(), title,
                    body.length() > 160 ? body.substring(0,157) + "..." : body, href, hit.channelId(), hit.channelScope()));
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == 403 || exception.getStatusCode().value() == 404) return Optional.empty();
            throw unavailable(exception);
        } catch (RestClientException exception) { throw unavailable(exception); }
    }
    private record Source(UUID id, UUID courseId, String status, String title, String body, String description) { }
    private ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Search source access is temporarily unavailable", cause);
    }
}
