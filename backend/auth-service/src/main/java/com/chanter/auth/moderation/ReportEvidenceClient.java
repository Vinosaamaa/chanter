package com.chanter.auth.moderation;

import com.chanter.auth.application.AuthUserRepository;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.ReportEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ReportEvidenceClient {
    private final AuthUserRepository users;
    private final ObjectMapper mapper;
    private final String token;
    private final Map<String,String> sources;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public ReportEvidenceClient(AuthUserRepository users, ObjectMapper mapper,
            @Value("${chanter.internal-service-token}") String token,
            @Value("${MESSAGE_SERVICE_URL:http://localhost:8083}") String message,
            @Value("${COMMUNITY_SERVICE_URL:http://localhost:8082}") String community,
            @Value("${MEDIA_SERVICE_URL:http://localhost:8084}") String media) {
        this.users=users; this.mapper=mapper; this.token=InternalServiceTokens.require(token);
        this.sources=Map.of("DM",message,"MESSAGE",message,"STUDY_SERVER",community,"RESOURCE",media);
    }

    public ReportEvidence read(UUID viewer, String type, UUID id) {
        ModerationRestrictions.requireType(type);
        if (type.equals("USER")) {
            var user=users.findById(id).orElseThrow(ReportEvidenceClient::unavailableSource);
            return new ReportEvidence(type,id,id,null,null,null,user.displayName(),"Account report",null);
        }
        URI endpoint=URI.create(sources.get(type)).resolve("/internal/v1/moderation/evidence/"+type+"/"+id+"?viewerId="+viewer);
        try {
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                    .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,token).GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()==403 || response.statusCode()==404) throw unavailableSource();
            if (response.statusCode()!=200 || response.body().length()>24000) throw unavailable();
            var evidence=mapper.readValue(response.body(),ReportEvidence.class);
            if (!type.equals(evidence.type()) || !id.equals(evidence.id())) throw unavailable();
            return evidence;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (IOException invalid) { throw unavailable(); }
    }

    private static ResponseStatusException unavailableSource() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,"The report source is not available to you");
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Report evidence is temporarily unavailable");
    }
}
