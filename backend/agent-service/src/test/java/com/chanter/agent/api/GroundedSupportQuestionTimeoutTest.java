package com.chanter.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import com.chanter.agent.application.GroundedSupportQuestionService;
import com.chanter.agent.application.LlmExecution;
import com.chanter.common.auth.AuthHeaders;
import jakarta.servlet.AsyncEvent;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GroundedSupportQuestionTimeoutTest {
    @Test void containerTimeoutCancelsWorkAndCannotProduceAnAuthoritativeCompleteEvent() throws Exception {
        var service = mock(GroundedSupportQuestionService.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var observed = new java.util.concurrent.atomic.AtomicReference<LlmExecution>();
        when(service.answerSupportQuestion(any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            LlmExecution execution = call.getArgument(5);
            observed.set(execution); started.countDown();
            release.await(5, TimeUnit.SECONDS);
            execution.check(); return null;
        });
        var mvc = MockMvcBuilders.standaloneSetup(new GroundedSupportQuestionController(service)).build();
        try {
            var result = mvc.perform(post("/api/v1/course-channels/{channel}/support-questions/{question}/assistant-answer/stream", UUID.randomUUID(), UUID.randomUUID())
                            .header(AuthHeaders.USER_ID, UUID.randomUUID().toString()))
                    .andExpect(request().asyncStarted()).andReturn();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var context = (MockAsyncContext) result.getRequest().getAsyncContext();
            for (var listener : context.getListeners()) listener.onTimeout(new AsyncEvent(context));
            result.getAsyncResult(1_000);
            mvc.perform(asyncDispatch(result));
            assertThat(result.getResponse().getContentAsString()).contains("event:status").doesNotContain("event:complete");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> observed.get().check()).hasMessageContaining("cancelled");
        } finally { release.countDown(); }
    }
}
