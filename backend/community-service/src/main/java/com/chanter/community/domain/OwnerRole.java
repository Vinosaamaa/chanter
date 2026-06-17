package com.chanter.community.domain;

import java.util.UUID;

public record OwnerRole(UUID userId, StudyServerRole role) {
}
