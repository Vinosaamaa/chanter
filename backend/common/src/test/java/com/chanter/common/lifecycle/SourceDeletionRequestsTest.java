package com.chanter.common.lifecycle;

import static org.assertj.core.api.Assertions.*;
import com.chanter.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

class SourceDeletionRequestsTest {
    JdbcTemplate jdbc; TransactionTemplate tx; SourceDeletionRequests requests; DurableOutbox outbox;
    @BeforeEach void setup() {
        var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(data); tx=new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute(TerminalReapplyStore.SCHEMA); jdbc.execute(DurableOutbox.SCHEMA); jdbc.execute(SourceDeletionRequests.SCHEMA);
        outbox=new DurableOutbox(jdbc,tx,"community",Clock.systemUTC());
        requests=new SourceDeletionRequests("STUDY_SERVER",jdbc,tx,outbox,new AccountDeletionProtocol(new ObjectMapper()));
    }
    @Test void owningAuthorizationAndOutboxFailureCannotLeaveAnAccessFence() {
        UUID target=UUID.randomUUID(),user=UUID.randomUUID();
        assertThatThrownBy(() -> requests.request(target,user,() -> { throw new IllegalArgumentException("not owner"); })).hasMessage("not owner");
        assertThat(requests.pending(target)).isFalse(); assertThat(outbox.claim()).isEmpty();
        jdbc.execute("ALTER TABLE lifecycle_source_requests ADD CONSTRAINT fixture_failure CHECK(target_id IS NULL)");
        try { assertThatThrownBy(() -> requests.request(target,user,() -> {})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { jdbc.execute("ALTER TABLE lifecycle_source_requests DROP CONSTRAINT fixture_failure"); }
        assertThat(requests.pending(target)).isFalse(); assertThat(outbox.claim()).isEmpty();
        var first=requests.request(target,user,() -> {});
        assertThat(requests.request(target,user,() -> { throw new AssertionError("Retry must retain original authorization"); })).isEqualTo(first);
        assertThatThrownBy(() -> requests.requireOpen(target)).hasMessageContaining("LIFECYCLE_DELETION_PENDING");
        assertThatThrownBy(() -> requests.request(target,UUID.randomUUID(),() -> {})).hasMessageContaining("404");
        var event=outbox.claim().orElseThrow();
        assertThat(event.event().kind()).isEqualTo(AccountDeletionProtocol.SOURCE_REQUEST);
        assertThat(event.event().payload()).doesNotContain(user.toString());
        outbox.delivered(event); assertThat(outbox.claim()).isEmpty();
    }
    @Test void concurrentRetriesSerializeAtTheSourceHeadAndProduceOneJob() throws Exception {
        UUID target=UUID.randomUUID(),user=UUID.randomUUID();
        var entered=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> requests.request(target,user,() -> {
                entered.countDown();
                try { if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var next=pool.submit(() -> requests.request(target,user,() -> {}));
            assertThatThrownBy(() -> next.get(100,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown(); assertThat(next.get(5,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(first.get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_outbox",Integer.class)).isEqualTo(1);
        } finally { release.countDown(); }
    }
}
