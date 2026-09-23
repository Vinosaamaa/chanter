package com.chanter.common.lifecycle;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Removes producer-held ordinary payloads under an existing terminal server and verified child scope. */
public final class ServerPayloadCleanup {
    private final JdbcTemplate jdbc;
    private final ObjectReader reader;
    public ServerPayloadCleanup(JdbcTemplate jdbc,ObjectMapper mapper) {
        this.jdbc=jdbc;reader=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    public void erase(TerminalJournal.Entry entry,String scopeTable) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Server payload erasure requires transaction");
        entry.validate();
        if(!entry.targetKind().equals("STUDY_SERVER") || !Set.of("lifecycle_deleted_server_scope_ids","lifecycle_scope_import_ids","lifecycle_recovery_scope_ids").contains(scopeTable)) throw invalid();
        jdbc.queryForObject("SELECT id FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",Integer.class);
        // Initial apply invokes the owning mutation before inserting its terminal row in this same transaction.
        // A replay may already have that row, but it must never substitute another terminal identity.
        if(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE target_kind='STUDY_SERVER' AND target_id=? AND (revision<>? OR event_id<>? OR digest<>?)",Integer.class,
                entry.targetId(),entry.revision(),entry.eventId(),entry.digest())!=0) throw invalid();
        String retire="UPDATE durable_outbox SET payload='{}',status='ERASED',lease_token=NULL,lease_until=NULL,last_error=NULL WHERE ";
        jdbc.update(retire+"""
            (destination='search' AND EXISTS(SELECT 1 FROM lifecycle_erased_content c WHERE c.target_kind='STUDY_SERVER'
              AND c.target_id=? AND c.source_kind=durable_outbox.kind AND durable_outbox.aggregate_key=c.source_kind || ':' || CAST(c.source_id AS VARCHAR)))
            OR (destination='notification' AND kind='NOTIFICATION' AND EXISTS(SELECT 1 FROM lifecycle_erased_content c
              WHERE c.target_kind='STUDY_SERVER' AND c.target_id=? AND durable_outbox.aggregate_key LIKE
                'NOTIFICATION:%:' || REPLACE(CASE WHEN c.source_kind IN ('QUESTION','QUESTION_PREVIEW') THEN 'SUPPORT_QUESTION'
                  WHEN c.source_kind='EVENT' THEN 'COMMUNITY_EVENT' ELSE c.source_kind END,'_','!_') || ':' || CAST(c.source_id AS VARCHAR) || ':%' ESCAPE '!'))
            """,entry.targetId(),entry.targetId());
        long after=0;
        while(true) {
            if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Server payload cleanup interrupted");
            var rows=jdbc.query("SELECT id,revision,payload FROM durable_outbox WHERE revision>? AND destination IN ('search','notification') AND payload<>'{}' ORDER BY revision LIMIT 256",
                    (rs,n)->new Row(rs.getObject(1,UUID.class),rs.getLong(2),rs.getString(3)),after);
            if(rows.isEmpty()) return;
            for(var row:rows) {
                try {
                    if(row.payload().length()>65536) throw invalid();
                    JsonNode payload=reader.readTree(row.payload());if(payload==null || !payload.isObject()) throw invalid();
                    if(entry.targetId().equals(id(payload,"studyServerId"))
                            || child(scopeTable,entry.targetId(),"COURSE",id(payload,"courseId"))
                            || child(scopeTable,entry.targetId(),"CHANNEL",id(payload,"channelId")))
                        jdbc.update(retire+"id=?",row.id());
                } catch(java.io.IOException malformed) {throw invalid();}
            }
            after=rows.getLast().revision();
        }
    }
    private boolean child(String table,UUID server,String kind,UUID id) {
        return id!=null && jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE study_server_id=? AND scope_kind=? AND scope_id=?",Integer.class,server,kind,id)==1;
    }
    private static UUID id(JsonNode payload,String field) {
        var value=payload.get(field);if(value==null || value.isNull()) return null;
        if(!value.isTextual()) throw invalid();
        try {return UUID.fromString(value.asText());} catch(IllegalArgumentException malformed) {throw invalid();}
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Unverified server payload scope");}
    private record Row(UUID id,long revision,String payload) { }
}
