package com.chanter.notification.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:answer-notification-retraction;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.events.dispatch-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnswerNotificationRetractionTest {
    private static final String TOKEN="test-internal-service-token-for-notification";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Test void exactAnswerRemovalSurvivesClaimedReplayAndPreservesHumanNotificationMetadata() throws Exception {
        var answer=new AnswerRetraction(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var created=notification(answer,10,false); deliver(created);
        var human=notification(answer,11,true); deliver(human);
        UUID humanId=jdbc.queryForObject("SELECT id FROM notifications WHERE source_type='SUPPORT_QUESTION' AND source_id=?",UUID.class,answer.questionId());
        jdbc.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP,done_at=CURRENT_TIMESTAMP WHERE id=?",humanId);
        var retained=jdbc.queryForMap("SELECT id,read_at,done_at,created_at FROM notifications WHERE id=?",humanId);
        var retract=new DurableEvent(UUID.randomUUID(),1,"message",20,AnswerRetraction.NOTIFICATION_KIND,answer.notificationKey(),
                mapper.writeValueAsString(new AnswerReconciliation(answer,"COMPLETE")));
        deliver(retract); deliver(retract); deliver(created);
        // Even a later direct source event cannot recreate the permanently retracted answer identity.
        deliver(notification(answer,21,false)); deliver(notification(answer,22,true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_id=?",Integer.class,answer.answerId())).isZero();
        assertThat(jdbc.queryForMap("SELECT id,read_at,done_at,created_at FROM notifications WHERE id=?",humanId)).isEqualTo(retained);
        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE id=?",String.class,humanId)).isEqualTo("Question update");
        assertThat(jdbc.queryForObject("SELECT body_preview FROM notifications WHERE id=?",String.class,humanId)).isNull();
        assertThat(jdbc.queryForObject("SELECT course_label FROM notifications WHERE id=?",String.class,humanId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE kind=? AND aggregate_key=?",Integer.class,AnswerRetraction.RECEIPT_KIND,answer.receiptKey())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload FROM durable_outbox WHERE aggregate_key=?",String.class,answer.receiptKey())).doesNotContain("private preview","private course");
    }
    @Test void retractionBeforeCreationAndWrongProducerOrScopeFailSafely() throws Exception {
        var answer=new AnswerRetraction(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        String payload=mapper.writeValueAsString(new AnswerReconciliation(answer,"COMPLETE"));
        var retract=new DurableEvent(UUID.randomUUID(),1,"message",20,AnswerRetraction.NOTIFICATION_KIND,answer.notificationKey(),payload);
        mvc.perform(post("/api/v1/internal/events").contentType("application/json").content(mapper.writeValueAsString(retract))).andExpect(status().isUnauthorized());
        var wrong=new DurableEvent(UUID.randomUUID(),1,"agent",20,AnswerRetraction.NOTIFICATION_KIND,answer.notificationKey(),payload);
        mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType("application/json")
                .content(mapper.writeValueAsString(wrong))).andExpect(status().isBadRequest());
        deliver(retract); deliver(notification(answer,10,false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_id=?",Integer.class,answer.answerId())).isZero();
    }
    @Test void migrationNeutralizesExistingAmbiguousPreviewWithoutRemovingReadOrDoneState() {
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").target("2").load().migrate();
        var legacy=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"sa",""));
        UUID id=UUID.randomUUID();
        legacy.update("INSERT INTO notifications(id,user_id,kind,filter_bucket,title,body_preview,course_label,href,source_type,source_id,created_at,read_at,done_at) VALUES (?,?,'SUPPORT_QUESTION_ANSWERED','MENTIONS','Your question was answered','private human overwrite','private course','/app/inbox','SUPPORT_QUESTION',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,UUID.randomUUID(),UUID.randomUUID());
        var metadata=legacy.queryForMap("SELECT id,read_at,done_at,created_at,href FROM notifications WHERE id=?",id);
        org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").load().migrate();
        assertThat(legacy.queryForMap("SELECT id,read_at,done_at,created_at,href FROM notifications WHERE id=?",id)).isEqualTo(metadata);
        assertThat(legacy.queryForObject("SELECT title FROM notifications WHERE id=?",String.class,id)).isEqualTo("Question update");
        assertThat(legacy.queryForObject("SELECT body_preview FROM notifications WHERE id=?",String.class,id)).isNull();
    }
    private DurableEvent notification(AnswerRetraction answer,long revision,boolean human) throws Exception {
        String type=human ? "SUPPORT_QUESTION" : AnswerRetraction.SOURCE_TYPE;
        UUID source=human ? answer.questionId() : answer.answerId();
        var body=new LinkedHashMap<String,Object>();
        body.put("userId",answer.authorId()); body.put("kind","SUPPORT_QUESTION_ANSWERED"); body.put("title","Your question was answered");
        body.put("bodyPreview","private preview"); body.put("courseLabel","private course"); body.put("href","/app/inbox");
        body.put("sourceType",type); body.put("sourceId",source); body.put("channelId",answer.channelId());
        return new DurableEvent(UUID.randomUUID(),1,"message",revision,"NOTIFICATION",NotificationEventWriter.aggregateKey(answer.authorId(),type,source,"SUPPORT_QUESTION_ANSWERED"),mapper.writeValueAsString(body));
    }
    private void deliver(DurableEvent event) throws Exception {
        mvc.perform(post("/api/v1/internal/events").header(AuthHeaders.INTERNAL_SERVICE_TOKEN,TOKEN).contentType("application/json")
                .content(mapper.writeValueAsString(event))).andExpect(status().isNoContent());
    }
}
