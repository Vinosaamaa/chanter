package com.chanter.message.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.chanter.common.auth.ModerationAccess;
import com.chanter.common.auth.ModerationAccess.Target;
import com.chanter.message.application.ChannelMessageRepository;
import com.chanter.message.application.ChannelMessageService;
import com.chanter.message.application.SocialMessagingService;
import com.chanter.message.domain.ChannelMessage;
import com.chanter.message.domain.ChannelScope;
import com.chanter.message.infra.TestChannelMessageAccessClient;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class MessageModerationTest {
    @Autowired ChannelMessageService channels;
    @Autowired ChannelMessageRepository repository;
    @Autowired SocialMessagingService social;
    @Autowired TestChannelMessageAccessClient access;
    @MockitoBean ModerationAccess moderation;
    final UUID user=UUID.randomUUID(), channel=UUID.randomUUID(), server=UUID.randomUUID();

    @BeforeEach void setup() {
        access.grant(channel,user,ChannelScope.COURSE,server,UUID.randomUUID());
        when(moderation.allowedSources(any(),anyList())).thenAnswer(call -> Set.copyOf(call.getArgument(1)));
    }

    @Test void quarantineFiltersHistoryAndDeniesDetailWithoutErasingEvidence() {
        var hidden=channels.postMessage(channel,user,ChannelScope.COURSE,"preserved evidence");
        var visible=channels.postMessage(channel,user,ChannelScope.COURSE,"visible");
        when(moderation.allowedSources(eq(user),anyList())).thenAnswer(call -> {
            List<Target> targets=call.getArgument(1);
            return targets.stream().filter(target -> !target.id().equals(hidden.id())).collect(java.util.stream.Collectors.toSet());
        });
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(moderation)
                .requireAllowed(user,List.of(new Target("MESSAGE",hidden.id())));
        assertThat(channels.listMessages(channel,user,ChannelScope.COURSE,Optional.empty(),Optional.empty())).containsExactly(visible);
        assertThatThrownBy(() -> channels.getMessage(channel,hidden.id(),user,ChannelScope.COURSE)).isInstanceOf(ResponseStatusException.class);
        assertThat(repository.findByIdAndChannelId(hidden.id(),channel)).contains(hidden);
    }

    @Test void restrictedServerCannotReadOrCreateMessages() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(moderation)
                .requireAllowed(user,List.of(new Target("STUDY_SERVER",server)));
        assertThatThrownBy(() -> channels.postMessage(channel,user,ChannelScope.COURSE,"denied")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> channels.listMessages(channel,user,ChannelScope.COURSE,Optional.empty(),Optional.empty())).isInstanceOf(ResponseStatusException.class);
        assertThat(repository.listByChannelSince(channel,Optional.empty(),Optional.empty(),200)).isEmpty();
    }

    @Test void fullHistoryPageUsesBoundedChecks() {
        for(int i=0;i<200;i++) repository.save(new ChannelMessage(UUID.randomUUID(),channel,user,"m"+i,Instant.now()));
        assertThat(channels.listMessages(channel,user,ChannelScope.COURSE,Optional.empty(),Optional.empty())).hasSize(200);
        verify(moderation,times(2)).allowedSources(eq(user),argThat(targets -> targets.size()==100));
    }

    @Test void quarantinedDirectMessageIsHiddenFromBothParticipantsHistory() {
        UUID peer=UUID.randomUUID();
        social.acceptFriendRequest(social.sendFriendRequest(user,peer).id(),peer);
        var hidden=social.sendDirectMessage(user,peer,"saved evidence");
        when(moderation.allowedSources(any(),anyList())).thenReturn(Set.of());
        assertThat(social.findDirectMessages(user,peer)).isEmpty();
        assertThat(social.findDirectMessages(peer,user)).isEmpty();
        verify(moderation).allowedSources(user,List.of(new Target("DM",hidden.id())));
        verify(moderation).allowedSources(peer,List.of(new Target("DM",hidden.id())));
    }

    @Test void blockCannotCommitInTheMiddleOfAnAuthorizedHistoryRead() throws Exception {
        UUID peer=UUID.randomUUID();
        social.acceptFriendRequest(social.sendFriendRequest(user,peer).id(),peer);
        social.sendDirectMessage(user,peer,"existing message");
        var reading=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        when(moderation.allowedSources(eq(user),anyList())).thenAnswer(call -> {
            reading.countDown();
            assertThat(release.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return Set.copyOf(call.getArgument(1));
        });
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var history=workers.submit(() -> social.findDirectMessages(user,peer));
            assertThat(reading.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var block=workers.submit(() -> social.blockUser(peer,user));
            try {
                assertThatThrownBy(() -> block.get(200,java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { release.countDown(); }
            assertThat(history.get(5,java.util.concurrent.TimeUnit.SECONDS)).hasSize(1);
            block.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertThatThrownBy(() -> social.findDirectMessages(user,peer)).isInstanceOf(ResponseStatusException.class);
        } finally { release.countDown(); }
    }
}
