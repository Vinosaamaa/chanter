package com.chanter.realtime.infra;

import com.chanter.realtime.application.PresenceStore;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisPresenceStore implements PresenceStore {

    private static final String KEY_PREFIX = "presence:user:";
    private static final Duration PRESENCE_TTL = Duration.ofMinutes(2);

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisPresenceStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void markOnline(UUID userId) {
        redisTemplate.opsForValue()
                .set(key(userId), "online", PRESENCE_TTL)
                .block();
    }

    @Override
    public void markOffline(UUID userId) {
        redisTemplate.delete(key(userId)).block();
    }

    @Override
    public boolean isOnline(UUID userId) {
        Boolean exists = redisTemplate.hasKey(key(userId)).block();
        return Boolean.TRUE.equals(exists);
    }

    private static String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
