package com.chanter.search.application;

import com.chanter.search.domain.SearchHit;
import java.util.Optional;
import java.util.UUID;

public interface SearchSourceClient {
    Optional<SearchHit> currentVisibleHit(SearchHit candidate, UUID studyServerId, UUID viewer);
}
