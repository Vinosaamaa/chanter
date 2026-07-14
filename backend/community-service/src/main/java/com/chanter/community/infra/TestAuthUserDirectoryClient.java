package com.chanter.community.infra;

import com.chanter.community.application.AuthUserDirectoryClient;
import com.chanter.community.domain.AuthUserProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestAuthUserDirectoryClient implements AuthUserDirectoryClient {

    private final Map<UUID, AuthUserProfile> profiles = new LinkedHashMap<>();

    public void register(UUID userId, String email, String displayName) {
        profiles.put(userId, new AuthUserProfile(userId, normalizeEmail(email), displayName));
    }

    public void reset() {
        profiles.clear();
    }

    @Override
    public Optional<AuthUserProfile> findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return profiles.values().stream()
                .filter(profile -> profile.email().equals(normalizedEmail))
                .findFirst();
    }

    @Override
    public List<AuthUserProfile> findByIds(List<UUID> userIds) {
        return userIds.stream()
                .distinct()
                .map(profiles::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
