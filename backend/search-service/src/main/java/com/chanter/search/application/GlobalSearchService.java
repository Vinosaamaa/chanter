package com.chanter.search.application;

import com.chanter.search.domain.SearchDocumentType;
import com.chanter.search.domain.SearchHit;
import com.chanter.search.infra.JdbcSearchIndexRepository;
import com.chanter.search.infra.JdbcSearchIndexRepository.IndexEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GlobalSearchService {

    private static final int DEFAULT_RESULT_LIMIT = 25;

    private final CommunityNavigationClient communityNavigationClient;
    private final MediaCatalogClient mediaCatalogClient;
    private final MessageFaqClient messageFaqClient;
    private final JdbcSearchIndexRepository searchIndexRepository;
    private final SearchSourceClient sourceClient;

    public GlobalSearchService(
            CommunityNavigationClient communityNavigationClient,
            MediaCatalogClient mediaCatalogClient,
            MessageFaqClient messageFaqClient,
            JdbcSearchIndexRepository searchIndexRepository,
            SearchSourceClient sourceClient
    ) {
        this.communityNavigationClient = communityNavigationClient;
        this.mediaCatalogClient = mediaCatalogClient;
        this.messageFaqClient = messageFaqClient;
        this.searchIndexRepository = searchIndexRepository;
        this.sourceClient = sourceClient;
    }

    public int reindexStudyServer(UUID studyServerId, UUID viewerUserId) {
        CommunityNavigationClient.StudyServerNavigation navigation =
                communityNavigationClient.fetchNavigation(studyServerId, viewerUserId);

        if (!navigation.canViewFullCatalog()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only instructors can refresh the search index"
            );
        }

        List<IndexEntry> entries = new ArrayList<>();
        Instant indexedAt = Instant.now();

        for (CommunityNavigationClient.CourseSummary course : navigation.courses()) {
            for (MediaCatalogClient.CourseResourceSummary resource
                    : mediaCatalogClient.listCourseResources(course.id(), viewerUserId)) {
                entries.add(new IndexEntry(
                        UUID.randomUUID(),
                        studyServerId,
                        course.id(),
                        course.title(),
                        SearchDocumentType.RESOURCE,
                        resource.id(),
                        resource.title(),
                        resource.fileName(),
                        indexedAt
                ));
            }

            for (MessageFaqClient.ApprovedFaqSummary faq
                    : messageFaqClient.listApprovedFaqs(course.id(), viewerUserId)) {
                entries.add(new IndexEntry(
                        UUID.randomUUID(),
                        studyServerId,
                        course.id(),
                        course.title(),
                        SearchDocumentType.FAQ,
                        faq.id(),
                        faq.question(),
                        faq.answer(),
                        indexedAt
                ));
            }
        }

        searchIndexRepository.replaceStudyServerIndex(studyServerId, entries);
        return entries.size();
    }

    public List<SearchHit> search(UUID studyServerId, UUID viewerUserId, String query) {
        CommunityNavigationClient.StudyServerNavigation navigation =
                communityNavigationClient.fetchNavigation(studyServerId, viewerUserId);

        List<UUID> visibleCourseIds = navigation.courses().stream()
                .map(CommunityNavigationClient.CourseSummary::id)
                .toList();

        List<SearchHit> candidates = searchIndexRepository.search(
                studyServerId,
                visibleCourseIds,
                query,
                DEFAULT_RESULT_LIMIT
        );

        if (candidates.isEmpty()) {
            return candidates;
        }

        Map<UUID, Set<UUID>> visibleResourceIdsByCourse = new HashMap<>();
        Map<UUID, Set<UUID>> visibleFaqIdsByCourse = new HashMap<>();

        return candidates.stream()
                .flatMap(hit -> {
                    if (hit.documentType() == SearchDocumentType.RESOURCE || hit.documentType() == SearchDocumentType.FAQ) {
                        return isVisibleToViewer(hit, viewerUserId, visibleResourceIdsByCourse, visibleFaqIdsByCourse)
                                ? java.util.stream.Stream.of(hit) : java.util.stream.Stream.<SearchHit>empty();
                    }
                    return sourceClient.currentVisibleHit(hit, studyServerId, viewerUserId).stream();
                })
                .map(hit -> new SearchHit(hit.documentType(), hit.courseId(), hit.courseId() == null ? navigation.studyServerName()
                        : navigation.courses().stream().filter(course -> course.id().equals(hit.courseId())).map(CommunityNavigationClient.CourseSummary::title).findFirst().orElse("Course"),
                        hit.sourceId(), hit.title(), hit.snippet(), hit.href(), hit.channelId(), hit.channelScope()))
                .toList();
    }

    private boolean isVisibleToViewer(
            SearchHit hit,
            UUID viewerUserId,
            Map<UUID, Set<UUID>> visibleResourceIdsByCourse,
            Map<UUID, Set<UUID>> visibleFaqIdsByCourse
    ) {
        UUID courseId = hit.courseId();

        if (hit.documentType() == SearchDocumentType.RESOURCE) {
            Set<UUID> visibleResourceIds = visibleResourceIdsByCourse.computeIfAbsent(
                    courseId,
                    id -> mediaCatalogClient.listCourseResources(id, viewerUserId).stream()
                            .map(MediaCatalogClient.CourseResourceSummary::id)
                            .collect(Collectors.toSet())
            );
            return visibleResourceIds.contains(hit.sourceId());
        }

        Set<UUID> visibleFaqIds = visibleFaqIdsByCourse.computeIfAbsent(
                courseId,
                id -> messageFaqClient.listApprovedFaqs(id, viewerUserId).stream()
                        .map(MessageFaqClient.ApprovedFaqSummary::id)
                        .collect(Collectors.toSet())
        );
        return visibleFaqIds.contains(hit.sourceId());
    }
}
