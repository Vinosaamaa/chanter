package com.chanter.auth.application;

import com.chanter.auth.domain.AuthUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository {

    AuthUser save(AuthUser user);

    AuthUser update(AuthUser user);

    /** Holds the account write lock until the caller's transaction ends; deleted accounts return false. */
    boolean lockActive(UUID id);

    Optional<AuthUser> findByEmail(String email);

    Optional<AuthUser> findById(UUID id);

    List<AuthUser> findByIds(List<UUID> ids);

    boolean existsByEmail(String email);

    void markEmailVerified(UUID userId);

    void updatePasswordHash(UUID userId, String passwordHash);
}
