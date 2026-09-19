package com.chanter.common.lifecycle;

import java.io.IOException;
import java.util.UUID;

/** Implemented by the data owner using fixed, explicit fields and account-scoped queries. */
@FunctionalInterface
public interface AccountExportProjection {
    void capture(UUID accountId, ExportSnapshotStore.Capture output) throws IOException;
}
