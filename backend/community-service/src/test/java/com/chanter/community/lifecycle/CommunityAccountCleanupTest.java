package com.chanter.community.lifecycle;

import static org.assertj.core.api.Assertions.*;
import com.chanter.common.lifecycle.*;
import com.chanter.community.application.*;
import com.chanter.community.domain.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:community-account-cleanup;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.events.dispatch-enabled=false"})
@ActiveProfiles("test")
class CommunityAccountCleanupTest {
    @Autowired StudyServerService servers;
    @Autowired CourseService courses;
    @Autowired CommunityAnnouncementService announcements;
    @Autowired CommunityEventService events;
    @Autowired OfficeHoursService officeHours;
    @Autowired TerminalReapplyStore terminal;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.chanter.community.infra.TestAuthUserDirectoryClient directory;

    @Test void personalPayloadsEraseAtomicallyWhileSharedCourseMovesToItsActualOwner() {
        UUID author=UUID.randomUUID(),successor=UUID.randomUUID();
        directory.register(author,author+"@fixture.test","Author"); directory.register(successor,successor+"@fixture.test","Owner");
        var server=servers.createStudyServer("Shared","Description",StudyServerType.PERSONAL,List.of(),author);
        var course=courses.createCourse(server.id(),author,"Retained shared course","Shared course description","Cohort");
        var announcement=announcements.createAnnouncement(server.id(),author,"Private title","Private announcement");
        var event=events.createEvent(server.id(),author,"Private event","Private description","Private location",Instant.now().plusSeconds(60),Instant.now().plusSeconds(3600),null,CommunityEventVisibility.HUB,null,null);
        var session=officeHours.scheduleOfficeHours(course.cohort().orElseThrow().id(),author,Instant.now().plusSeconds(60),Instant.now().plusSeconds(3600));
        // Fixture represents an already-committed ownership transfer, not a new transfer API.
        jdbc.update("UPDATE study_servers SET owner_user_id=? WHERE id=?",successor,server.id());
        jdbc.update("UPDATE study_server_roles SET user_id=? WHERE study_server_id=? AND user_id=?",successor,server.id(),author);
        var entry=entry(author);
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> { terminal.applyTerminal(entry); status.setRollbackOnly(); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_announcements WHERE id=?",Integer.class,announcement.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT instructor_user_id FROM courses WHERE id=?",UUID.class,course.id())).isEqualTo(author);
        apply(entry); apply(entry);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_announcements WHERE id=?",Integer.class,announcement.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_events WHERE id=?",Integer.class,event.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM office_hours_sessions WHERE id=?",Integer.class,session.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_erased_content WHERE target_id=?",Integer.class,author)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT title FROM courses WHERE id=?",String.class,course.id())).isEqualTo("Retained shared course");
        assertThat(jdbc.queryForObject("SELECT instructor_user_id FROM courses WHERE id=?",UUID.class,course.id())).isEqualTo(successor);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_roles WHERE course_id=? AND user_id=? AND role='INSTRUCTOR'",Integer.class,course.id(),successor)).isEqualTo(1);
        assertThatThrownBy(() -> courses.assignCourseInstructor(course.id(),successor,author,null)).hasMessageContaining("410");
        assertThatThrownBy(() -> courses.createCohortInvitation(course.cohort().orElseThrow().id(),successor,author+"@fixture.test")).hasMessageContaining("410");
        var cleanup=tx.execute(status -> terminal.cleanup(entry));
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }

    @Test void restoredTerminalOwnerIsNotReassignedToAnInventedSuccessor() {
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
        var server=servers.createStudyServer("Older server",null,StudyServerType.PERSONAL,List.of(),owner);
        var course=courses.createCourse(server.id(),owner,"Older course",null,"Cohort");
        var entry=entry(owner); apply(entry);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM study_servers WHERE id=?",UUID.class,server.id())).isEqualTo(owner);
        assertThat(jdbc.queryForObject("SELECT instructor_user_id FROM courses WHERE id=?",UUID.class,course.id())).isEqualTo(owner);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_retained_courses WHERE account_id=?",Integer.class,owner)).isZero();
        assertThatThrownBy(() -> courses.updateCourseMetadata(course.id(),other,"Unavailable",null)).hasMessageContaining("410");
        var cleanup=new TransactionTemplate(transactions).execute(status -> terminal.cleanup(entry));
        assertThat(cleanup).isEqualTo(TerminalReapplyStore.Cleanup.PENDING);
    }

    @Test void inFlightAnnouncementCommitsBeforeTerminalErasureAndLateWritesAreDenied() throws Exception {
        UUID author=UUID.randomUUID();
        var server=servers.createStudyServer("Race",null,StudyServerType.PERSONAL,List.of(),author);
        var entry=entry(author); var created=new CountDownLatch(1); var release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var writing=executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var result=announcements.createAnnouncement(server.id(),author,"In flight","Must erase");
                created.countDown(); await(release); return result;
            }));
            assertThat(created.await(5,TimeUnit.SECONDS)).isTrue();
            var deleting=executor.submit(() -> apply(entry));
            try { assertThatThrownBy(() -> deleting.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            var written=writing.get(5,TimeUnit.SECONDS); deleting.get(5,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_announcements WHERE id=?",Integer.class,written.id())).isZero();
            assertThatThrownBy(() -> announcements.createAnnouncement(server.id(),author,"Late","Must not recreate")).hasMessageContaining("410");
        } finally { release.countDown(); }
    }
    private void apply(TerminalJournal.Entry entry) { new TransactionTemplate(transactions).executeWithoutResult(status -> terminal.applyTerminal(entry)); }
    private TerminalJournal.Entry entry(UUID account) {
        long revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM lifecycle_terminal_targets",Long.class);
        UUID event=UUID.randomUUID(); Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return new TerminalJournal.Entry(revision,event,"ACCOUNT",account,"DELETE",now,TerminalJournal.RETENTION_POLICY,TerminalJournal.GENESIS,
                TerminalJournal.digest(revision,event,"ACCOUNT",account,now,TerminalJournal.GENESIS));
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Fixture latch timeout"); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
}
