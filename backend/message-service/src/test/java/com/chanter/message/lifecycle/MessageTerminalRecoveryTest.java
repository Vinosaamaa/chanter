package com.chanter.message.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.chanter.common.lifecycle.*;
import com.chanter.common.events.*;
import com.chanter.message.domain.*;
import com.chanter.message.infra.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:message-terminal;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.events.dispatch-enabled=false","chanter.recovery-mode=true",
        "chanter.recovery-restore-id=33333333-3333-4333-8333-333333333333"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageTerminalRecoveryTest {
    @Autowired TerminalReapplyStore terminal;
    @Autowired DeletedScopeStore current;
    @Autowired RecoveryScopeStore historical;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcChannelMessageRepository messages;
    @Autowired JdbcSupportQuestionRepository questions;
    @Autowired JdbcSupportQuestionReplyRepository replies;
    @Autowired JdbcApprovedFaqRepository faqs;
    @Autowired JdbcSocialMessagingRepository social;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test void accountErasesAuthoredCopiesAtomicallyAndAcknowledgesLateAnswerWithoutNotification() throws Exception {
        UUID user=UUID.randomUUID(),peer=UUID.randomUUID(),channel=UUID.randomUUID(),course=UUID.randomUUID();
        var question=question(user,channel);
        var other=question(peer,UUID.randomUUID());
        replies.save(new SupportQuestionReply(UUID.randomUUID(),question.id(),peer,"other reply to deleted question",Instant.now()));
        replies.save(new SupportQuestionReply(UUID.randomUUID(),other.id(),user,"deleted author reply",Instant.now()));
        var faq=faqs.save(new ApprovedFaq(UUID.randomUUID(),course,"derived question","derived answer",peer,Instant.now(),Instant.now()),List.of(question.id()));
        social.saveDirectMessage(new DirectMessage(UUID.randomUUID(),user,peer,"private sent",Instant.now()));
        social.saveDirectMessage(new DirectMessage(UUID.randomUUID(),peer,user,"private received",Instant.now()));
        social.saveFriendRequest(new FriendRequest(UUID.randomUUID(),user,peer,FriendRequestStatus.ACCEPTED,Instant.now()));
        social.saveUserBlock(user,peer);
        var entry=entry("ACCOUNT",user);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> { terminal.applyTerminal(entry); tx.setRollbackOnly(); });
        assertThat(questions.findByIdAndChannelId(channel,question.id())).isPresent();
        assertThat(social.findDirectMessages(user,peer)).hasSize(2);
        apply(entry); apply(entry);
        assertThat(questions.findByIdAndChannelId(channel,question.id())).isEmpty();
        assertThat(questions.findByIdAndChannelId(other.channelId(),other.id())).isPresent();
        assertThat(replies.findBySupportQuestionId(other.id())).isEmpty();
        assertThat(faqs.findByIdAndCourseId(faq.id(),course)).isEmpty();
        assertThat(social.findDirectMessages(user,peer)).isEmpty();
        assertThat(social.areFriends(user,peer)).isFalse();
        assertThat(social.findBlockedUserIds(user)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content WHERE target_id=? AND source_kind='QUESTION_PREVIEW'",Integer.class,user)).isEqualTo(2);
        assertThatThrownBy(() -> messages.save(new ChannelMessage(UUID.randomUUID(),channel,user,"late",Instant.now())))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> social.saveDirectMessage(new DirectMessage(UUID.randomUUID(),peer,user,"late",Instant.now())))
                .isInstanceOf(ResponseStatusException.class);
        var answer=new AcceptedAnswerStatus(UUID.randomUUID(),question.id(),channel,user,"AI_ANSWERED");
        var event=new DurableEvent(UUID.randomUUID(),1,"agent",1,AcceptedAnswerStatus.KIND,answer.aggregateKey(),mapper.writeValueAsString(answer));
        for(int retry=0;retry<2;retry++) mvc.perform(post("/api/v1/internal/events")
                .header(com.chanter.common.auth.AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-message")
                .contentType("application/json").content(mapper.writeValueAsString(event))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox WHERE aggregate_key LIKE ?",Integer.class,"%"+question.id()+"%")).isZero();
        assertThat(cleanup(entry)).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }
    @Test void restoredHistoricalScopeErasesLegacyRowsOnlyAfterBothVerifiedKindsAndRollsBackFinalPage() {
        UUID server=UUID.randomUUID(),course=UUID.randomUUID(),oldChannel=UUID.randomUUID();
        var question=question(UUID.randomUUID(),oldChannel);
        var unrelated=question(UUID.randomUUID(),UUID.randomUUID());
        var faq=faqs.save(new ApprovedFaq(UUID.randomUUID(),course,"historical","answer",UUID.randomUUID(),Instant.now(),Instant.now()),List.of(question.id()));
        var entry=entry("STUDY_SERVER",server); apply(entry);
        current.accept(new DeletedScope.Import(entry,page(entry,"COURSE",List.of(),entry.digest())));
        current.accept(new DeletedScope.Import(entry,page(entry,"CHANNEL",List.of(),entry.digest())));
        assertThat(questions.findByIdAndChannelId(oldChannel,question.id())).isPresent();
        UUID operation=UUID.randomUUID();
        historical.accept(new RecoveryScope.Import(historical.restoreId(),operation,current.scopeDigest(entry,"COURSE"),entry,
                page(entry,"COURSE",List.of(course),historical.basis(entry,"COURSE"))));
        var last=new RecoveryScope.Import(historical.restoreId(),operation,current.scopeDigest(entry,"CHANNEL"),entry,
                page(entry,"CHANNEL",List.of(oldChannel),historical.basis(entry,"CHANNEL")));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> { historical.accept(last); tx.setRollbackOnly(); });
        assertThat(questions.findByIdAndChannelId(oldChannel,question.id())).isPresent();
        historical.accept(last); historical.accept(last);
        assertThat(questions.findByIdAndChannelId(oldChannel,question.id())).isEmpty();
        assertThat(faqs.findByIdAndCourseId(faq.id(),course)).isEmpty();
        assertThat(questions.findByIdAndChannelId(unrelated.channelId(),unrelated.id())).isPresent();
        assertThatThrownBy(() -> question(UUID.randomUUID(),oldChannel)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> faqs.save(new ApprovedFaq(UUID.randomUUID(),course,"late","answer",UUID.randomUUID(),Instant.now(),Instant.now()),List.of()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(cleanup(entry)).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }
    @Test void concurrentAccountDeletionPrecedesPairCreationAndLateMessageWrite() throws Exception {
        UUID user=UUID.randomUUID(),peer=UUID.randomUUID(); var entry=entry("ACCOUNT",user);
        var deleted=new CountDownLatch(1); var release=new CountDownLatch(1); var attempted=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var deletion=pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                terminal.applyTerminal(entry); deleted.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Fixture did not release deletion"); }
                catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }));
            try {
                assertThat(deleted.await(5,TimeUnit.SECONDS)).isTrue();
                var writing=pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    attempted.countDown(); social.lockPair(user,peer);
                    social.saveDirectMessage(new DirectMessage(UUID.randomUUID(),user,peer,"racing",Instant.now()));
                }));
                assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> writing.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); deletion.get(5,TimeUnit.SECONDS);
                assertThatThrownBy(() -> writing.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(ResponseStatusException.class);
            } finally { release.countDown(); }
        }
        assertThat(social.findDirectMessages(user,peer)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_pair_locks WHERE first_user_id=? OR second_user_id=?",Integer.class,user,user)).isZero();
    }
    private SupportQuestion question(UUID user,UUID channel) {
        return questions.saveSupportQuestion(new SupportQuestion(UUID.randomUUID(),UUID.randomUUID(),channel,user,"private question",
                SupportQuestionStatus.UNANSWERED,UUID.randomUUID().toString(),Instant.now()));
    }
    private void apply(TerminalJournal.Entry entry) { new TransactionTemplate(transactions).executeWithoutResult(tx -> terminal.applyTerminal(entry)); }
    private TerminalReapplyStore.Cleanup cleanup(TerminalJournal.Entry entry) { return new TransactionTemplate(transactions).execute(tx -> terminal.cleanup(entry)); }
    private TerminalJournal.Entry entry(String kind,UUID target) {
        long revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM lifecycle_terminal_targets",Long.class);
        UUID event=UUID.randomUUID(); Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return new TerminalJournal.Entry(revision,event,kind,target,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(revision,event,kind,target,now,TerminalJournal.GENESIS));
    }
    private static DeletedScope.Page page(TerminalJournal.Entry entry,String kind,List<UUID> ids,String basis) {
        String digest=DeletedScope.startDigest(basis,kind,ids.size()); for(UUID id:ids) digest=DeletedScope.nextDigest(digest,id);
        return new DeletedScope.Page(1,entry.targetId(),entry.revision(),entry.eventId(),entry.digest(),kind,DeletedScope.START,ids.size(),digest,ids,null);
    }
}
