package com.chanter.message.api;

import static com.chanter.message.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.ModerationAccess;
import com.chanter.common.auth.ModerationAccess.Target;
import com.chanter.message.application.SocialMessagingService;
import com.chanter.message.infra.JdbcSocialMessagingRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlockingEnforcementTest {
    @Autowired SocialMessagingService service;
    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcSocialMessagingRepository repository;
    @MockitoBean ModerationAccess moderation;

    private UUID request(UUID first,UUID second) {
        return service.sendFriendRequest(first,second).id();
    }
    private void friends(UUID first,UUID second) { service.acceptFriendRequest(request(first,second),second); }

    @Test void blockedPendingRequestCannotBecomeAFriendship() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        UUID request=request(first,second);
        service.blockUser(first,second);
        assertThatThrownBy(() -> service.acceptFriendRequest(request,second)).isInstanceOf(ResponseStatusException.class);
        assertThat(repository.areFriends(first,second)).isFalse();
    }

    @Test void unblockingIsOwnedByTheCallerAndPreservesTheOtherPersonsBlock() throws Exception {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),stranger=UUID.randomUUID();
        friends(first,second);
        service.blockUser(first,second); service.blockUser(second,first);
        mvc.perform(delete("/api/v1/user-blocks/"+second).with(asUser(stranger))).andExpect(status().isNoContent());
        assertThat(service.findBlockedUserIds(first)).contains(second);
        mvc.perform(delete("/api/v1/user-blocks/"+second).with(asUser(first))).andExpect(status().isNoContent());
        assertThat(service.findBlockedUserIds(first)).doesNotContain(second);
        assertThatThrownBy(() -> service.sendDirectMessage(first,second,"still blocked")).isInstanceOf(ResponseStatusException.class);
        mvc.perform(delete("/api/v1/user-blocks/"+first).with(asUser(second))).andExpect(status().isNoContent());
        assertThat(service.sendDirectMessage(first,second,"allowed again").body()).isEqualTo("allowed again");
    }

    @Test void suspendedPeerCannotReceiveNewMessagesOrKeepCallEligibility() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(); friends(first,second);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(moderation).requireAllowed(first,List.of(new Target("USER",second)));
        assertThatThrownBy(() -> service.sendDirectMessage(first,second,"restricted")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.requireDirectMessageCallAccess(first,second)).isInstanceOf(ResponseStatusException.class);
        assertThat(repository.findDirectMessages(first,second)).isEmpty();
    }

    @Test void suspensionWhileWaitingForPairLockPreventsTheMessageWrite() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(); friends(first,second);
        JdbcSocialMessagingRepository target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(repository);
        doAnswer(call -> {
            call.callRealMethod();
            doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(moderation).requireAllowed(first,List.of(new Target("USER",second)));
            return null;
        }).when(target).lockPair(first,second);
        assertThatThrownBy(() -> service.sendDirectMessage(first,second,"suspended while waiting"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(repository.findDirectMessages(first,second)).isEmpty();
    }

    @Test void concurrentBlockCannotCommitBetweenEligibilityReadAndMessageWrite() throws Exception {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(); friends(first,second);
        var checked=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(call -> {
            boolean blocked=(boolean)call.callRealMethod();
            checked.countDown();
            if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test did not release sender");
            return blocked;
        }).when(repository).isBlocked(first,second);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var send=executor.submit(() -> service.sendDirectMessage(first,second,"before block"));
            try {
                assertThat(checked.await(5,TimeUnit.SECONDS)).isTrue();
                var block=executor.submit(() -> service.blockUser(second,first));
                try { assertThatThrownBy(() -> block.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
                finally { release.countDown(); }
                send.get(5,TimeUnit.SECONDS); block.get(5,TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
        assertThatThrownBy(() -> service.sendDirectMessage(first,second,"after block")).isInstanceOf(ResponseStatusException.class);
        assertThat(repository.findDirectMessages(first,second)).extracting(message -> message.body()).containsExactly("before block");
    }
}
