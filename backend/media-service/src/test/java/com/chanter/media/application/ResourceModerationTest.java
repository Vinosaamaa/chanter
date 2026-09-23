package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chanter.common.auth.ModerationAccess;
import com.chanter.common.auth.ModerationAccess.Target;
import com.chanter.media.domain.CourseResource;
import com.chanter.media.infra.TestCourseResourceAccessClient;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceModerationTest {
    @Autowired MockMvc mvc;
    @Autowired CourseResourceService service;
    @Autowired ResourceWorker worker;
    @Autowired ResourceLifecycle lifecycle;
    @Autowired JdbcClient jdbc;
    @Autowired TestCourseResourceAccessClient access;
    @Autowired com.chanter.common.lifecycle.TerminalReapplyStore terminal;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean ModerationAccess moderation;
    @MockitoBean MalwareScanner scanner;
    @MockitoSpyBean PrivateResourceStorage storage;
    UUID course, teacher, learner;

    @BeforeEach void prepare() throws Exception {
        jdbc.sql("DELETE FROM course_resources").update();
        jdbc.sql("UPDATE media_storage_budget SET reserved_bytes=0,foreground_requests=0,maintenance_requests=0").update();
        access.clear();
        course=UUID.randomUUID(); teacher=UUID.randomUUID(); learner=UUID.randomUUID();
        access.grantInstructorUpload(course,teacher); access.grantLearnerView(course,learner);
        when(scanner.scan(any())).thenReturn(MalwareScanner.Verdict.CLEAN);
        when(moderation.allowedSources(any(),anyList())).thenAnswer(call -> Set.copyOf(call.<List<Target>>getArgument(1)));
    }

    private CourseResource upload() {
        var resource=service.uploadCourseResource(course,teacher,"Notes",true,
                new MockMultipartFile("file","notes.txt","text/plain","bounded notes".getBytes(java.nio.charset.StandardCharsets.UTF_8)),UUID.randomUUID(),null);
        for(int attempt=0; attempt<5 && !lifecycle.find(resource.id()).orElseThrow().state().equals("AVAILABLE"); attempt++) worker.runOnce();
        assertThat(lifecycle.find(resource.id()).orElseThrow().state()).isEqualTo("AVAILABLE");
        return lifecycle.find(resource.id()).orElseThrow();
    }

    @Test void quarantineHidesOnlyTheRestrictedResourceAndBlocksPublicAndWorkerBytes() throws Exception {
        var restricted=upload(); var visible=upload();
        clearInvocations(storage);
        when(moderation.allowedSources(eq(learner),anyList())).thenReturn(Set.of(new Target("RESOURCE",visible.id())));
        doAnswer(call -> {
            if(call.<List<Target>>getArgument(1).contains(new Target("RESOURCE",restricted.id())))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return null;
        }).when(moderation).requireAllowed(nullable(UUID.class),anyList());
        assertThat(service.listCourseResources(course,learner)).extracting(CourseResource::id).containsExactly(visible.id());
        assertThatThrownBy(() -> service.getCourseResource(restricted.id(),learner)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.deleteCourseResource(restricted.id(),teacher)).isInstanceOf(ResponseStatusException.class);
        assertThat(lifecycle.find(restricted.id()).orElseThrow().state()).isEqualTo("AVAILABLE");
        assertThatThrownBy(() -> { try(var ignored=service.downloadCourseResource(restricted.id(),learner).content()) { } }).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> { try(var ignored=service.downloadForIngestion(restricted.id(),course,restricted.sha256()).content()) { } }).isInstanceOf(ResponseStatusException.class);
        verify(storage,never()).open(restricted.storageKey());
    }

    @Test void restrictionAcceptedDuringProviderReadWinsBeforeAnyBytesLeaveForIngestion() throws Exception {
        var resource=upload(); var restricted=new AtomicBoolean();
        doAnswer(call -> {
            if(restricted.get()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return null;
        }).when(moderation).requireAllowed(isNull(),anyList());
        doAnswer(call -> { var content=call.callRealMethod(); restricted.set(true); return content; })
                .when(storage).open(resource.storageKey());
        assertThatThrownBy(() -> { try(var ignored=service.downloadForIngestion(resource.id(),course,resource.sha256()).content()) { } })
                .isInstanceOfSatisfying(ResponseStatusException.class,failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(moderation,times(2)).requireAllowed(isNull(),eq(List.of(new Target("STUDY_SERVER",resource.studyServerId()),new Target("RESOURCE",resource.id()))));
    }

    @Test void studyServerRestrictionAndAuthorityOutageFailClosed() {
        upload();
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(moderation).requireAllowed(eq(learner),anyList());
        assertThatThrownBy(() -> service.listCourseResources(course,learner)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(moderation).requireAllowed(eq(teacher),anyList());
        assertThatThrownBy(() -> service.usage(course,teacher)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void originalRequesterCanRecoverLostDeleteResponseAfterCanonicalTerminalAndMetadataErasure() {
        var resource=upload();
        var original=service.deleteCourseResource(resource.id(),teacher);
        var instant=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        long revision=jdbc.sql("SELECT COALESCE(MAX(revision),0)+1 FROM lifecycle_terminal_targets").query(Long.class).single();
        UUID event=UUID.randomUUID();
        var entry=new com.chanter.common.lifecycle.TerminalJournal.Entry(revision,event,"RESOURCE",resource.id(),"DELETE",instant,
                com.chanter.common.lifecycle.TerminalJournal.RETENTION_POLICY,com.chanter.common.lifecycle.TerminalJournal.GENESIS,
                com.chanter.common.lifecycle.TerminalJournal.digest(revision,event,"RESOURCE",resource.id(),instant,com.chanter.common.lifecycle.TerminalJournal.GENESIS));
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> terminal.applyTerminal(entry));
        jdbc.sql("DELETE FROM course_resources WHERE id=:id").param("id",resource.id()).update();
        clearInvocations(moderation);
        doThrow(new ResponseStatusException(HttpStatus.GONE)).when(moderation).requireAllowed(any(),anyList());
        assertThat(service.deleteCourseResource(resource.id(),teacher)).isEqualTo(original);
        assertThatThrownBy(() -> service.deleteCourseResource(resource.id(),learner)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(moderation,never()).requireAllowed(any(),anyList());
    }

    @Test void reportEvidenceRequiresInternalAuthenticationAndCurrentSourceScopeWithoutStorageSecrets() throws Exception {
        var resource=upload();
        String endpoint="/internal/v1/moderation/evidence/RESOURCE/"+resource.id();
        mvc.perform(get(endpoint).param("viewerId",learner.toString())).andExpect(status().isUnauthorized());
        mvc.perform(get(endpoint).header("X-Chanter-Internal-Service-Token","test-internal-service-token-for-media")
                .param("viewerId",UUID.randomUUID().toString())).andExpect(status().isNotFound());
        var result=mvc.perform(get(endpoint).header("X-Chanter-Internal-Service-Token","test-internal-service-token-for-media")
                .param("viewerId",learner.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorId").value(teacher.toString()))
                .andExpect(jsonPath("$.studyServerId").value(resource.studyServerId().toString()))
                .andExpect(jsonPath("$.sourceFingerprint").value(resource.sha256())).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(resource.storageKey(),"storageKey","storageBackend");
    }
}
