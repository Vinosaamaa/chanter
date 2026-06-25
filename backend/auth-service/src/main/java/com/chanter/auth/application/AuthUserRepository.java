package com.chanter.auth.application;

import com.chanter.auth.domain.AuthUser;
import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository {

    AuthUser save(AuthUser user);

    Optional<AuthUser> findByEmail(String email);

    Optional<AuthUser> findById(UUID id);

    boolean existsByEmail(String email);
}
