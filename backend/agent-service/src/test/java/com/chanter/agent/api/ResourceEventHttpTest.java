package com.chanter.agent.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.chanter.agent.application.ResourceIngestionJobs;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:agent-resource-events;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.events.dispatch-enabled=false","chanter.ingestion.worker-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceEventHttpTest {
    private static final String TOKEN = "test-internal-service-token-for-agent";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ResourceIngestionJobs jobs;
    @Autowired com.chanter.agent.application.StudyAssistantAnswerRepository answers;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void terminalMediaEventRetractsAnotherLearnersSavedAnswerBeforeReportingDeletionComplete() throws Exception {
        UUID resource=UUID.randomUUID(),server=UUID.randomUUID(),learner=UUID.randomUUID(),question=UUID.randomUUID(),channel=UUID.randomUUID();
        jdbc.update("INSERT INTO study_assistant_installs VALUES (?,?,?,?)",UUID.randomUUID(),server,UUID.randomUUID(),java.sql.Timestamp.from(java.time.Instant.now()));
        var answer=new com.chanter.agent.domain.StudyAssistantAnswer(UUID.randomUUID(),question,channel,server,learner,"private question","private answer",
                com.chanter.agent.domain.AnswerConfidence.HIGH,false,java.util.List.of(new com.chanter.agent.domain.StudyAssistantAnswerSource(UUID.randomUUID(),resource,"source.txt","private excerpt")),java.time.Instant.now());
        answers.saveAnswer(answer,com.chanter.agent.domain.InvocationType.GROUNDED_ANSWER);
        // An older implementation may already have committed the terminal cursor without retracting saved answers.
        jdbc.update("INSERT INTO durable_event_cursor(producer,aggregate_key,revision,event_id,deleted) VALUES ('media',?,49,?,TRUE)","RESOURCE:"+resource,UUID.randomUUID());
        var change=new ResourceChanged(resource,null,null,null,null,false,true);
        var event=new DurableEvent(UUID.randomUUID(),1,"media",50,"RESOURCE_CHANGED","RESOURCE:"+resource,mapper.writeValueAsString(change));
        mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isNoContent());
        assertThat(answers.findBySupportQuestionId(question)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_answer_retractions WHERE answer_id=? AND receipt_state='PENDING'",Integer.class,answer.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind=? AND aggregate_key=?",Integer.class,
                ResourceDeletionReceipt.KIND,new ResourceDeletionReceipt(resource,event.id()).key())).isZero();
        var answerReceipt=new AnswerReconciliation(new AnswerRetraction(answer.id(),question,channel,learner),"COMPLETE");
        var reconciled=new DurableEvent(UUID.randomUUID(),1,"notification",60,AnswerRetraction.RECEIPT_KIND,answerReceipt.answer().receiptKey(),mapper.writeValueAsString(answerReceipt));
        jdbc.execute("ALTER TABLE durable_outbox ADD CONSTRAINT reject_resource_receipt CHECK(kind<>'RESOURCE_DELETE_COMPLETE' OR aggregate_key<>'"+new ResourceDeletionReceipt(resource,event.id()).key()+"')");
        try {assertThatThrownBy(() -> mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reconciled)))).hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class);}
        finally {jdbc.execute("ALTER TABLE durable_outbox DROP CONSTRAINT reject_resource_receipt");}
        assertThat(jdbc.queryForObject("SELECT receipt_state FROM lifecycle_answer_retractions WHERE answer_id=?",String.class,answer.id())).isEqualTo("PENDING");
        for(int retry=0;retry<2;retry++) {
            mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(reconciled))).andExpect(status().isNoContent());
            mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isNoContent());
        }
        var completed=jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE kind=? AND aggregate_key=?",String.class,
                ResourceDeletionReceipt.KIND,new ResourceDeletionReceipt(resource,event.id()).key());
        assertThat(mapper.readValue(completed,ResourceDeletionReceipt.class)).isEqualTo(new ResourceDeletionReceipt(resource,event.id()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind=? AND aggregate_key=?",Integer.class,
                ResourceDeletionReceipt.KIND,new ResourceDeletionReceipt(resource,event.id()).key())).isEqualTo(1);
    }

    @Test void jsonNullIsRejectedAsBadRequestBeforeConsumption() throws Exception {
        mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("null")).andExpect(status().isBadRequest());
    }

    @Test void internalEventAuthenticationScopeAndReplayAreEnforced() throws Exception {
        var resource = UUID.randomUUID();
        var change = new ResourceChanged(resource, UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), "notes.txt", true, false);
        var event = new DurableEvent(UUID.randomUUID(), 1, "media", 1, "RESOURCE_CHANGED", "RESOURCE:" + resource, mapper.writeValueAsString(change));
        mvc.perform(post("/api/v1/internal/events").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event)))
                .andExpect(status().isUnauthorized());
        for (int delivery = 0; delivery < 2; delivery++) {
            mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event))).andExpect(status().isNoContent());
        }
        assertThat(jobs.status(resource, event.id()).status()).isEqualTo("PENDING");
        var invalid = new DurableEvent(UUID.randomUUID(), 1, "community", 2, "RESOURCE_CHANGED", "RESOURCE:" + resource, mapper.writeValueAsString(change));
        mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(invalid))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/internal/resource-ingestion/{id}/status", resource).param("eventId", event.id().toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/internal/resource-ingestion/{id}/status", resource).param("eventId", event.id().toString())
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)).andExpect(status().isOk());
        // Leave no queued work for other integration tests sharing this application context.
        var deleted = new ResourceChanged(resource, null, null, null, null, false, true);
        var terminal = new DurableEvent(UUID.randomUUID(), 1, "media", 3, "RESOURCE_CHANGED", "RESOURCE:" + resource, mapper.writeValueAsString(deleted));
        mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(terminal))).andExpect(status().isNoContent());
    }
}
