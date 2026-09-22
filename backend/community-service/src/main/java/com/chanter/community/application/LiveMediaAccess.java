package com.chanter.community.application;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.ModerationAccess;
import com.chanter.community.domain.ChannelKind;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LiveMediaAccess {
    private final StudyServerRepository servers;
    private final CourseRepository courses;
    private final ModerationAccess moderation;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final Semaphore calls = new Semaphore(32);
    private final URI realtime;
    private final String token;

    public LiveMediaAccess(StudyServerRepository servers, CourseRepository courses, ModerationAccess moderation,
            @Value("${REALTIME_SERVICE_HTTP_URL:http://localhost:8087}") String realtime,
            @Value("${chanter.internal-service-token}") String token) {
        this.servers = servers;
        this.courses = courses;
        this.moderation = moderation;
        this.realtime = URI.create(realtime);
        InternalServiceTokens.requireBytes(token);
        this.token = token;
    }

    public void requireAllowed(String room, UUID user) {
        if (room.startsWith("voice-")) {
            UUID channel = roomId(room, "voice-");
            UUID server;
            var course = courses.findAccessibleChannel(channel, user).orElse(null);
            if (course != null) {
                if (course.kind() != ChannelKind.VOICE) throw denied();
                server = courses.findStudyServerIdByCourseId(course.courseId()).orElseThrow(LiveMediaAccess::denied);
            } else {
                var shared = servers.findChannelById(channel).orElseThrow(LiveMediaAccess::denied);
                if (shared.kind() != ChannelKind.VOICE || !servers.isStudyServerMember(shared.studyServerId(), user)) throw denied();
                server = shared.studyServerId();
            }
            moderation.requireAllowed(user, List.of(new ModerationAccess.Target("STUDY_SERVER", server)));
        } else if (room.startsWith("dm-call-")) {
            UUID call = roomId(room, "dm-call-");
            moderation.requireAccount(user);
            requireCurrentCall(call, user);
        } else {
            throw denied();
        }
    }

    private void requireCurrentCall(UUID call, UUID user) {
        if (!calls.tryAcquire()) throw unavailable();
        try {
            var request = HttpRequest.newBuilder(realtime.resolve("/internal/v1/dm-calls/"+call+"/media-access?userId="+user))
                    .timeout(Duration.ofSeconds(3)).header(AuthHeaders.INTERNAL_SERVICE_TOKEN, token).GET().build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 204) return;
            if (status == 401 || status == 403 || status == 404 || status == 409) throw denied();
            throw unavailable();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException unavailable) {
            throw unavailable();
        } finally {
            calls.release();
        }
    }

    private static UUID roomId(String room, String prefix) {
        try {
            UUID id = UUID.fromString(room.substring(prefix.length()));
            if (!room.equals(prefix + id)) throw denied();
            return id;
        } catch (IllegalArgumentException invalid) {
            throw denied();
        }
    }
    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Current media access required");
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media access authority unavailable");
    }
}
