package com.chanter.message.api;

import java.util.List;
import java.util.UUID;

public record UserBlockListResponse(List<UUID> blockedUserIds) {
}
