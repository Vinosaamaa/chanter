package com.chanter.realtime.websocket;

import com.chanter.realtime.application.PersistedChannelMessage;
import com.chanter.realtime.domain.RealtimeChannelScope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class RealtimeSubscriptionHub {

    private final ObjectMapper objectMapper;
    private final Map<UUID, Set<Subscription>> subscriptionsByChannel = new ConcurrentHashMap<>();
    private final Map<String, SubscriptionState> subscriptionsBySession = new ConcurrentHashMap<>();

    public RealtimeSubscriptionHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void subscribe(WebSocketSession session, UUID userId, UUID channelId, RealtimeChannelScope channelScope) {
        unsubscribeAll(session);

        Subscription subscription = new Subscription(session, userId, channelId, channelScope);
        subscriptionsByChannel.computeIfAbsent(channelId, ignored -> ConcurrentHashMap.newKeySet())
                .add(subscription);
        subscriptionsBySession.put(session.getId(), new SubscriptionState(channelId, channelScope));
    }

    public void unsubscribeAll(WebSocketSession session) {
        SubscriptionState state = subscriptionsBySession.remove(session.getId());
        if (state == null) {
            return;
        }

        subscriptionsByChannel.computeIfPresent(state.channelId(), (channelId, channelSubscriptions) -> {
            channelSubscriptions.removeIf(subscription -> subscription.session().getId().equals(session.getId()));
            return channelSubscriptions.isEmpty() ? null : channelSubscriptions;
        });
    }

    public Mono<Void> publishMessage(PersistedChannelMessage message, RealtimeChannelScope channelScope) {
        Set<Subscription> channelSubscriptions = subscriptionsByChannel.get(message.channelId());
        if (channelSubscriptions == null || channelSubscriptions.isEmpty()) {
            return Mono.empty();
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "type", "message",
                    "channelId", message.channelId().toString(),
                    "channelScope", channelScope.name(),
                    "payload", Map.of(
                            "id", message.id().toString(),
                            "channelId", message.channelId().toString(),
                            "senderUserId", message.senderUserId().toString(),
                            "body", message.body(),
                            "createdAt", message.createdAt().toString()
                    )
            ));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }

        return Flux.fromIterable(channelSubscriptions)
                .flatMap(subscription -> subscription.session().send(
                                Mono.just(subscription.session().textMessage(payload))
                        )
                        .onErrorResume(error -> {
                            removeSubscription(subscription);
                            return Mono.empty();
                        }))
                .then();
    }

    private void removeSubscription(Subscription subscription) {
        subscriptionsBySession.remove(subscription.session().getId());
        subscriptionsByChannel.computeIfPresent(subscription.channelId(), (channelId, channelSubscriptions) -> {
            channelSubscriptions.remove(subscription);
            return channelSubscriptions.isEmpty() ? null : channelSubscriptions;
        });
    }

    private record Subscription(
            WebSocketSession session,
            UUID userId,
            UUID channelId,
            RealtimeChannelScope channelScope
    ) {
    }

    private record SubscriptionState(UUID channelId, RealtimeChannelScope channelScope) {
    }
}
