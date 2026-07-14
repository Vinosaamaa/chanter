package com.chanter.community.application;

import com.chanter.community.domain.AuthUserProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthUserDirectoryClient {

    Optional<AuthUserProfile> findByEmail(String email);

    List<AuthUserProfile> findByIds(List<UUID> userIds);
}
