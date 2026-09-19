package com.chanter.common.lifecycle;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Source-owned current authorization for retained third-party content, files and AI evidence. */
@FunctionalInterface
public interface ExportSnapshotAccess {
    void require(UUID accountId, String kind, UUID targetId);
    static ExportSnapshotAccess denyProtected() {
        return (account, kind, target) -> { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EXPORT_ACCESS_CHECK_UNAVAILABLE"); };
    }
}
