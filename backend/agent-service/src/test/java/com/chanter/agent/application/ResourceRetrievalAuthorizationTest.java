package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chanter.agent.domain.*;
import com.chanter.agent.infra.HttpCourseResourceCatalogClient;
import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class ResourceRetrievalAuthorizationTest {
    private final UUID course = UUID.randomUUID(), viewer = UUID.randomUUID(), resource = UUID.randomUUID(), chunk = UUID.randomUUID();
    private final ResourceChunkRepository chunks = mock(ResourceChunkRepository.class);
    private final com.chanter.agent.infra.JdbcVectorSearch embeddings = mock(com.chanter.agent.infra.JdbcVectorSearch.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final EmbeddingModelRouter modelRouter = mock(EmbeddingModelRouter.class);
    private final UUID server = UUID.randomUUID();
    private final String sourceHash = "a".repeat(64);
    private HttpServer media;
    private HttpCourseResourceCatalogClient catalog;
    private VectorRetrievalService retrieval;
    private String status = "AVAILABLE";
    private String ingestionStatus = "READY";
    private boolean approved = true;
    private UUID metadataCourse = course;
    private int mediaStatus = 200;

    @BeforeEach void setup() throws Exception {
        media = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        media.createContext("/", exchange -> {
            int code = viewer.toString().equals(exchange.getRequestHeaders().getFirst(AuthHeaders.USER_ID)) ? mediaStatus : 403;
            var rows = status.equals("MISSING") ? List.of() : List.of(Map.of("id", resource, "courseId", metadataCourse,
                    "title", "Guide", "fileName", "guide.md", "status", status, "aiApproved", approved, "ingestionStatus", ingestionStatus,
                    "studyServerId", server, "sha256", sourceHash));
            byte[] body = new ObjectMapper().writeValueAsBytes(Map.of("courseResources", rows));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        media.start();
        catalog = new HttpCourseResourceCatalogClient("http://127.0.0.1:" + media.getAddress().getPort(), 1, 1, "test-internal-service-token-for-agent");
        retrieval = new VectorRetrievalService(embeddings, modelRouter, catalog, 0.35);
        // Persisted/injected vectors survive the metadata transition, as a late indexing request can.
        when(modelRouter.pinned()).thenReturn(embeddingClient);
        when(embeddingClient.metadata()).thenReturn(new EmbeddingModel("fixture", "test", "fixture", "v1", 8));
        when(embeddingClient.embed("question")).thenReturn(new float[]{1, 0, 0, 0, 0, 0, 0, 0});
        when(embeddings.nearest(any(), eq(course), anyList(), any(), eq(5), eq(0.35))).thenReturn(List.of(
                new VectorRetrievalService.RankedChunk(chunk, resource, course, 0, 0, 14, "stale evidence", "guide.md", 1, "fixture")));
        when(chunks.findByResourceId(resource)).thenReturn(List.of(
                new ResourceChunk(chunk, resource, course, 0, 0, 14, "stale evidence", "hash", "guide.md", Instant.now())));
    }

    @AfterEach void stop() { media.stop(0); }

    @ParameterizedTest @ValueSource(strings = {"PROCESSING", "REJECTED", "FAILED", "DELETED", "MISSING"})
    void staleChunksCannotBeRetrievedAfterResourceBecomesUnavailable(String nextStatus) {
        assertThat(retrieval.retrieve("question", course, viewer, Set.of(resource), 5)).hasSize(1);
        status = nextStatus;
        assertThat(retrieval.retrieve("question", course, viewer, Set.of(resource), 5)).isEmpty();
    }

    @Test void approvalCourseAndViewerAreRequiredEvenWithAnExistingGrantAndVector() {
        approved = false;
        assertThat(retrieval.retrieve("question", course, viewer, Set.of(resource), 5)).isEmpty();
        approved = true; metadataCourse = UUID.randomUUID();
        assertThat(retrieval.retrieve("question", course, viewer, Set.of(resource), 5)).isEmpty();
        metadataCourse = course;
        assertThat(retrieval.retrieve("question", course, UUID.randomUUID(), Set.of(resource), 5)).isEmpty();
        verifyNoInteractions(embeddingClient, chunks, embeddings);
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "PROCESSING", "FAILED", "OCR_REQUIRED", "ENCRYPTED", "MALFORMED", "UNSUPPORTED", "EMPTY", "LIMIT_EXCEEDED", "UNKNOWN"})
    void staleChunksCannotBeRetrievedWhileCurrentExtractionIsNotReady(String nextStatus) {
        assertThat(retrieval.retrieve("question", course, viewer, Set.of(resource), 5)).hasSize(1);
        ingestionStatus = nextStatus;
        assertThat(retrieval.retrieve("question", course, viewer, Set.of(resource), 5)).isEmpty();
    }

    @Test void mediaFailureDoesNotFallBackToStaleChunks() {
        mediaStatus = 503;
        assertThatThrownBy(() -> retrieval.retrieve("question", course, viewer, Set.of(resource), 5)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(embeddingClient, chunks, embeddings);
    }
    @Test void tokenlessQueryHasNoSemanticEvidence() {
        assertThat(retrieval.retrieve("?! ...",course,viewer,Set.of(resource),5)).isEmpty();
        verifyNoInteractions(embeddingClient,embeddings);
    }

    @Test void datastoreReceivesTheLiveAuthorizedScopeAndSourceVersion() {
        assertThat(retrieval.retrieve("question", course, viewer, Set.of(resource), 5)).hasSize(1);
        verify(embeddings).nearest(any(), eq(course), eq(List.of(new com.chanter.agent.infra.JdbcVectorSearch.AuthorizedResource(
                resource, server, null, sourceHash))), any(), eq(5), eq(0.35));
        verifyNoInteractions(chunks);
    }

    @ParameterizedTest @ValueSource(strings = {"PROCESSING", "REJECTED", "FAILED", "DELETED", "MISSING", "UNAPPROVED", "OTHER_COURSE", "MEDIA_FAILED"})
    void instructorCannotReleasePreviouslyStoredCitationForUnavailableResource(String nextStatus) {
        var access = mock(SupportQuestionChannelAccessClient.class);
        var assistant = mock(StudyAssistantRepository.class);
        var content = mock(CourseResourceContentClient.class);
        var faqs = mock(ApprovedFaqClient.class);
        UUID channel = UUID.randomUUID(), studyServer = UUID.randomUUID(), install = UUID.randomUUID();
        when(access.requireAccess(channel, viewer)).thenReturn(new SupportQuestionChannelAccessClient.SupportQuestionChannelAccess(channel, course, studyServer, "questions", false, true));
        when(assistant.findInstallByStudyServerId(studyServer)).thenReturn(java.util.Optional.of(new StudyAssistantInstall(install, studyServer, viewer, Instant.now())));
        when(assistant.findGrantsByInstallId(install)).thenReturn(List.of(
                new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_CHANNEL, channel),
                new StudyAssistantGrant(UUID.randomUUID(), install, GrantType.COURSE_RESOURCE, resource)));
        status = nextStatus;
        if (nextStatus.equals("UNAPPROVED")) { status = "AVAILABLE"; approved = false; }
        if (nextStatus.equals("OTHER_COURSE")) { status = "AVAILABLE"; metadataCourse = UUID.randomUUID(); }
        if (nextStatus.equals("MEDIA_FAILED")) mediaStatus = 503;
        var guard = new AiEvidenceAuthorization(access, assistant, catalog, content, faqs);
        assertThatThrownBy(() -> guard.requireCurrent(channel, viewer, List.of(new GroundingEngine.SourceCitation(resource, "Guide", "stale evidence"))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(content);
    }

    @Test void chunkToolReauthorizesAnEarlierScopeBeforeReturningContent() {
        var grants = new AssistantGrantScopeService(mock(StudyAssistantService.class), catalog);
        var scope = new AssistantGrantScopeService.GrantScope(UUID.randomUUID(), course, viewer, null, Set.of(resource), List.of(), List.of());
        var tool = new com.chanter.agent.application.tools.FetchResourceChunkTool(grants, chunks);
        var arguments = Map.<String, Object>of("resourceId", resource, "chunkIndex", 0);
        assertThat(tool.invoke(scope, arguments)).isNotNull();
        status = "DELETED";
        assertThatThrownBy(() -> tool.invoke(scope, arguments)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void resourceIndexToolRejectsChunksFromAnotherCourse() {
        when(chunks.findByResourceId(resource)).thenReturn(List.of(
                new ResourceChunk(chunk, resource, UUID.randomUUID(), 0, 0, 14, "wrong course", "hash", "guide.md", Instant.now())));
        var grants = new AssistantGrantScopeService(mock(StudyAssistantService.class), catalog);
        var scope = new AssistantGrantScopeService.GrantScope(UUID.randomUUID(), course, viewer, null, Set.of(resource), List.of(), List.of());
        var tool = new com.chanter.agent.application.tools.FetchResourceChunkTool(grants, chunks);
        assertThatThrownBy(() -> tool.invoke(scope, Map.of("resourceId", resource, "chunkIndex", 0))).isInstanceOf(ResponseStatusException.class);
    }
}
