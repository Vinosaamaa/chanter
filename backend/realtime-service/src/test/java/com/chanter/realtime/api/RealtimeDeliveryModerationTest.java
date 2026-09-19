package com.chanter.realtime.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.chanter.common.auth.ModerationAccess;
import com.chanter.realtime.application.*;
import com.chanter.realtime.domain.RealtimeChannelScope;
import com.chanter.realtime.websocket.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest
@ActiveProfiles("test")
class RealtimeDeliveryModerationTest {
    @Autowired RealtimeSubscriptionHub channels;
    @Autowired SocialRealtimeHub social;
    @MockitoBean ChannelSubscriptionAuthorizer channelAccess;
    @MockitoBean DirectMessageCallAuthorizer pairAccess;
    @MockitoBean ModerationAccess moderation;
    @MockitoBean SocialFriendsClient friends;
    @MockitoBean PresenceStore presence;

    @BeforeEach void setup() {
        when(friends.listFriendUserIds(any())).thenReturn(Mono.just(List.of()));
        when(pairAccess.requireCallAccess(any(),any())).thenReturn(Mono.empty());
    }

    @Test void formerSubscriberDoesNotReceiveContentAfterAuthorityChanges() {
        UUID user=UUID.randomUUID(),channel=UUID.randomUUID(); var received=new ArrayList<String>();
        var session=session(received);
        channels.subscribe(session,user,channel,RealtimeChannelScope.COURSE);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(channelAccess).requireSubscribeAccess(channel,user,RealtimeChannelScope.COURSE);
        channels.publishMessage(new PersistedChannelMessage(UUID.randomUUID(),channel,UUID.randomUUID(),"hidden",Instant.now()),RealtimeChannelScope.COURSE).block(Duration.ofSeconds(5));
        assertThat(received).isEmpty();
        reset(channelAccess);
        channels.publishMessage(new PersistedChannelMessage(UUID.randomUUID(),channel,UUID.randomUUID(),"needs fresh subscription",Instant.now()),RealtimeChannelScope.COURSE).block(Duration.ofSeconds(5));
        assertThat(received).isEmpty();
    }

    @Test void newlyBlockedPairDoesNotReceiveAnAlreadyPersistedMessage() {
        UUID sender=UUID.randomUUID(),recipient=UUID.randomUUID(); var received=new ArrayList<String>();
        var session=session(received);
        social.connect(session,recipient).block(Duration.ofSeconds(5)); received.clear();
        when(pairAccess.requireCallAccess(sender,recipient)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));
        try {
            social.publishDirectMessage(new PersistedDirectMessage(UUID.randomUUID(),sender,recipient,"saved before block",Instant.now())).block(Duration.ofSeconds(5));
            assertThat(received).isEmpty();
        } finally { social.disconnect(session).block(Duration.ofSeconds(5)); }
    }

    @Test void presenceSnapshotRemovesPeersWhoNoLongerHavePairAccess() {
        UUID user=UUID.randomUUID(),peer=UUID.randomUUID(); var received=new ArrayList<String>();
        var session=session(received);
        when(friends.listFriendUserIds(user)).thenReturn(Mono.just(List.of(peer)));
        when(presence.isOnline(peer)).thenReturn(true);
        social.refreshPresence(session,user).block(Duration.ofSeconds(5));
        assertThat(received.getLast()).contains(peer.toString());
        when(pairAccess.requireCallAccess(user,peer)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));
        social.refreshPresence(session,user).block(Duration.ofSeconds(5));
        assertThat(received.getLast()).contains("presence_snapshot").doesNotContain(peer.toString());
    }

    @Test void authorityOutageAndTransportFailureAreNotReportedAsSuccessfulDelivery() {
        UUID sender=UUID.randomUUID(),recipient=UUID.randomUUID(); var session=session(new ArrayList<>());
        social.connect(session,recipient).block(Duration.ofSeconds(5));
        when(pairAccess.requireCallAccess(sender,recipient)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)));
        try {
            assertThatThrownBy(() -> social.publishDirectMessage(new PersistedDirectMessage(UUID.randomUUID(),sender,recipient,"saved message",Instant.now())).block(Duration.ofSeconds(5)))
                    .isInstanceOf(ResponseStatusException.class);
            doReturn(Mono.error(new IllegalStateException("transport failed"))).when(session).send(any());
            assertThatThrownBy(() -> social.deliverEventToUser(recipient,Map.of("type","call_ended")).block(Duration.ofSeconds(5)))
                    .isInstanceOf(IllegalStateException.class).hasMessage("transport failed");
        } finally { social.disconnect(session).block(Duration.ofSeconds(5)); }
    }

    private static WebSocketSession session(List<String> received) {
        var session=mock(WebSocketSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(session.textMessage(anyString())).thenAnswer(call -> new WebSocketMessage(WebSocketMessage.Type.TEXT,
                DefaultDataBufferFactory.sharedInstance.wrap(((String)call.getArgument(0)).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        when(session.send(any())).thenAnswer(call -> Flux.from((org.reactivestreams.Publisher<WebSocketMessage>)call.getArgument(0))
                .doOnNext(message -> received.add(message.getPayloadAsText())).then());
        return session;
    }
}
