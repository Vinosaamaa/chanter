package com.chanter.agent.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.chanter.agent.application.*;
import com.chanter.common.auth.AuthHeaders;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalEmbeddingVersionsControllerTest {
    @Test void privateControlsRejectMissingTokenAndUnconfiguredModels() throws Exception {
        var versions=mock(EmbeddingVersionStore.class);
        var models=mock(EmbeddingModelRouter.class);
        var jobs=mock(EmbeddingRebuildJobs.class);
        var mvc=MockMvcBuilders.standaloneSetup(new InternalEmbeddingVersionsController(versions,models,jobs,
                "operator-test-token-with-at-least-32-bytes")).build();
        mvc.perform(get("/api/v1/internal/embedding-versions")).andExpect(status().isUnauthorized());
        verifyNoInteractions(versions,models,jobs);
        when(models.client("unknown")).thenThrow(new IllegalStateException());
        mvc.perform(post("/api/v1/internal/embedding-versions/stage")
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"operator-test-token-with-at-least-32-bytes")
                .contentType(MediaType.APPLICATION_JSON).content("{\"modelId\":\"unknown\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(versions);
        when(versions.state()).thenReturn(new EmbeddingVersionStore.State("current",null,null));
        when(versions.progress()).thenReturn(List.of());
        mvc.perform(get("/api/v1/internal/embedding-versions")
                .header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"operator-test-token-with-at-least-32-bytes"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.serving.active").value("current"));
    }
}
