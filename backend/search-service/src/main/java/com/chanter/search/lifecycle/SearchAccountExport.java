package com.chanter.search.lifecycle;

import com.chanter.common.lifecycle.AccountExportProjection;
import com.chanter.common.lifecycle.ExportSnapshotStore;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SearchAccountExport implements AccountExportProjection {
    @Override public void capture(UUID accountId, ExportSnapshotStore.Capture output) throws IOException {
        output.jsonLines("coverage", rows -> rows.add(Map.of("source", "search", "storage", "Derived shared content index.",
                "personalSearchHistory", "This service does not store account-linked search history or account-specific index documents.",
                "omissions", List.of("Shared index content is not a separate canonical personal record. Authored records are supplied by their owning source services.",
                "Exporting this account does not grant access to other accounts' shared or private index documents."))));
    }
}
