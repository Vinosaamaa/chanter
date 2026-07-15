package com.chanter.auth.application;

import java.util.Optional;
import java.util.UUID;

public interface OAuthAccountRepository {

    Optional<UUID> findUserId(String provider, String providerSubject);

    void link(UUID id, UUID userId, String provider, String providerSubject);
}
