package com.chanter.agent.infra;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;

/** Immutable scope plus a one-way acceptance claim. Evidence is erased at terminal settlement or expiry. */
@Repository
public class NativeRequestRepository {
    private final JdbcClient jdbc;
    private final Clock clock;
    public NativeRequestRepository(JdbcClient jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public void issue(Request request) {
        expire();
        jdbc.sql("""
                INSERT INTO native_companion_requests(id,channel_id,question_id,user_id,session_id,installation_id,model,
                    evidence_json,prompt_hash,evidence_hash,outcome,accept_until)
                VALUES (:id,:channel,:question,:user,:session,:installation,:model,:evidence,:prompt,:hash,'ISSUED',:until)
                """).param("id", request.id()).param("channel", request.channel()).param("question", request.question())
                .param("user", request.user()).param("session", request.session()).param("installation", request.installation())
                .param("model", request.model()).param("evidence", request.evidenceJson()).param("prompt", request.promptHash())
                .param("hash", request.evidenceHash()).param("until", request.acceptUntil().atOffset(ZoneOffset.UTC)).update();
    }
    public Request claim(UUID id, UUID channel, UUID question, UUID user, UUID session, UUID installation) {
        expire();
        var row = jdbc.sql("SELECT * FROM native_companion_requests WHERE id=:id AND user_id=:user AND session_id=:session")
                .param("id", id).param("user", user).param("session", session).query((rs, number) -> new Request(
                        rs.getObject("id", UUID.class), rs.getObject("channel_id", UUID.class), rs.getObject("question_id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getObject("session_id", UUID.class), rs.getObject("installation_id", UUID.class),
                        rs.getString("model"), rs.getString("evidence_json"), rs.getString("prompt_hash"), rs.getString("evidence_hash"),
                        rs.getObject("accept_until", OffsetDateTime.class).toInstant())).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Native request not found"));
        if (!row.channel().equals(channel) || !row.question().equals(question) || !row.installation().equals(installation))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Native request scope mismatch");
        int changed = jdbc.sql("""
                UPDATE native_companion_requests SET outcome='ACCEPTING'
                WHERE id=:id AND outcome='ISSUED' AND accept_until>:now AND evidence_json IS NOT NULL
                """).param("id", id).param("now", clock.instant().atOffset(ZoneOffset.UTC)).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Native result is expired or already attempted");
        return row;
    }
    public void finish(UUID id, boolean accepted, Integer input, Integer output) {
        jdbc.sql("""
                UPDATE native_companion_requests SET outcome=:outcome,evidence_json=NULL,client_input_tokens=:input,client_output_tokens=:output
                WHERE id=:id AND outcome='ACCEPTING'
                """).param("outcome", accepted ? "ACCEPTED" : "REJECTED").param("input", input).param("output", output).param("id", id).update();
    }
    @Scheduled(fixedDelay = 60000)
    public void expire() {
        jdbc.sql("""
                UPDATE native_companion_requests SET outcome='EXPIRED',evidence_json=NULL
                WHERE (outcome='ISSUED' AND accept_until<=:now) OR (outcome='ACCEPTING' AND accept_until<=:abandoned)
                """).param("now", clock.instant().atOffset(ZoneOffset.UTC))
                .param("abandoned", clock.instant().minusSeconds(60).atOffset(ZoneOffset.UTC)).update();
    }
    public record Request(UUID id, UUID channel, UUID question, UUID user, UUID session, UUID installation, String model,
                          String evidenceJson, String promptHash, String evidenceHash, Instant acceptUntil) {}
}
