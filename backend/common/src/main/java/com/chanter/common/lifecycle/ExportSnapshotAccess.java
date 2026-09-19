package com.chanter.common.lifecycle;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Source-owned current authorization for retained third-party content, files and AI evidence. */
@FunctionalInterface
public interface ExportSnapshotAccess {
    void require(UUID accountId, ExportSnapshotStore.AccessScope scope);
    default void requireAll(UUID accountId, java.util.List<ExportSnapshotStore.AccessScope> scopes) {
        for (var scope : scopes) require(accountId, scope);
    }
    static ExportSnapshotAccess denyProtected() {
        return (account, scope) -> { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EXPORT_ACCESS_CHECK_UNAVAILABLE"); };
    }
}
