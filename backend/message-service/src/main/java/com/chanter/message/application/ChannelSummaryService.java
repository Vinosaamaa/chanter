package com.chanter.message.application;

import com.chanter.message.domain.ApprovedFaq;
import com.chanter.message.domain.ChannelMessage;
import com.chanter.message.domain.SupportQuestion;
import com.chanter.message.domain.SupportQuestionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChannelSummaryService {

    private static final int MAX_TIMELINE_EVENTS = 12;
    private static final int MAX_FOLLOW_UP_QUESTIONS = 5;
    private static final int MAX_TEXT_ITEMS = 4;
    private static final int MIN_WINDOW_DAYS = 1;
    private static final int MAX_WINDOW_DAYS = 90;

    private final SupportQuestionRepository supportQuestionRepository;
    private final ChannelMessageRepository channelMessageRepository;
    private final ApprovedFaqRepository approvedFaqRepository;
    private final CourseChannelAccessClient courseChannelAccessClient;
    private final FaqCandidateGrouper faqCandidateGrouper;
    private final Clock clock;

    public ChannelSummaryService(
            SupportQuestionRepository supportQuestionRepository,
            ChannelMessageRepository channelMessageRepository,
            ApprovedFaqRepository approvedFaqRepository,
            CourseChannelAccessClient courseChannelAccessClient,
            FaqCandidateGrouper faqCandidateGrouper,
            Clock clock
    ) {
        this.supportQuestionRepository = supportQuestionRepository;
        this.channelMessageRepository = channelMessageRepository;
        this.approvedFaqRepository = approvedFaqRepository;
        this.courseChannelAccessClient = courseChannelAccessClient;
        this.faqCandidateGrouper = faqCandidateGrouper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ChannelSummary generateSummary(UUID channelId, UUID viewerUserId, int windowDays) {
        CourseChannelAccess access = courseChannelAccessClient.requireAccess(channelId, viewerUserId);
        if (!access.canViewUnansweredSupportQuestions()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Instructors can view Channel Summaries");
        }

        int normalizedWindowDays = normalizeWindowDays(windowDays);
        Instant windowEnd = clock.instant();
        Instant windowStart = windowEnd.minus(normalizedWindowDays, ChronoUnit.DAYS);
        Instant previousWindowStart = windowStart.minus(normalizedWindowDays, ChronoUnit.DAYS);

        List<SupportQuestion> currentQuestions = supportQuestionRepository.findByChannelIdAndCreatedAtBetween(
                channelId,
                windowStart,
                windowEnd
        );
        List<SupportQuestion> previousQuestions = supportQuestionRepository.findByChannelIdAndCreatedAtBetween(
                channelId,
                previousWindowStart,
                windowStart
        );

        Set<UUID> currentSupportMessageIds = supportQuestionMessageIds(currentQuestions);
        Set<UUID> previousSupportMessageIds = supportQuestionMessageIds(previousQuestions);

        long currentReplies = channelMessageRepository.countByChannelCreatedBetweenExcludingIds(
                channelId,
                windowStart,
                windowEnd,
                currentSupportMessageIds
        );
        long previousReplies = channelMessageRepository.countByChannelCreatedBetweenExcludingIds(
                channelId,
                previousWindowStart,
                windowStart,
                previousSupportMessageIds
        );

        long currentViews = estimateViews(currentQuestions.size(), currentReplies);
        long previousViews = estimateViews(previousQuestions.size(), previousReplies);

        int currentResolvedPercent = resolvedPercent(currentQuestions);
        int previousResolvedPercent = resolvedPercent(previousQuestions);

        ChannelSummaryMetrics metrics = new ChannelSummaryMetrics(
                metric(currentQuestions.size(), previousQuestions.size()),
                metric(currentReplies, previousReplies),
                metric(currentViews, previousViews),
                metric(currentResolvedPercent, previousResolvedPercent)
        );

        ChannelSummaryDigest digest = buildDigest(access.courseId(), currentQuestions, windowStart, windowEnd);
        List<ChannelSummaryTimelineEvent> timeline = buildTimeline(
                access.courseId(),
                currentQuestions,
                channelMessageRepository.listByChannelCreatedBetweenExcludingIds(
                        channelId,
                        windowStart,
                        windowEnd,
                        currentSupportMessageIds,
                        200
                ),
                windowStart,
                windowEnd
        );

        return new ChannelSummary(
                channelId,
                access.channelName(),
                normalizedWindowDays,
                windowStart,
                windowEnd,
                clock.instant(),
                metrics,
                digest,
                timeline
        );
    }

    private static int normalizeWindowDays(int windowDays) {
        if (windowDays < MIN_WINDOW_DAYS || windowDays > MAX_WINDOW_DAYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "windowDays must be between " + MIN_WINDOW_DAYS + " and " + MAX_WINDOW_DAYS
            );
        }
        return windowDays;
    }

    private static ChannelSummaryMetric metric(long currentValue, long previousValue) {
        return new ChannelSummaryMetric(currentValue, deltaPercent(currentValue, previousValue));
    }

    private static int deltaPercent(long currentValue, long previousValue) {
        if (previousValue == 0L) {
            return currentValue > 0L ? 100 : 0;
        }
        return Math.toIntExact(Math.round((currentValue - previousValue) * 100.0 / previousValue));
    }

    private static long estimateViews(long questionCount, long replyCount) {
        return questionCount * 25L + replyCount * 4L;
    }

    private static int resolvedPercent(List<SupportQuestion> questions) {
        if (questions.isEmpty()) {
            return 0;
        }

        long resolvedCount = questions.stream()
                .filter(question -> question.status() == SupportQuestionStatus.AI_ANSWERED)
                .count();
        return Math.toIntExact(Math.round(resolvedCount * 100.0 / questions.size()));
    }

    private static Set<UUID> supportQuestionMessageIds(List<SupportQuestion> questions) {
        return questions.stream()
                .map(SupportQuestion::channelMessageId)
                .collect(Collectors.toSet());
    }

    private static boolean isWithinWindow(Instant instant, Instant windowStart, Instant windowEnd) {
        return !instant.isBefore(windowStart) && instant.isBefore(windowEnd);
    }

    private ChannelSummaryDigest buildDigest(
            UUID courseId,
            List<SupportQuestion> questions,
            Instant windowStart,
            Instant windowEnd
    ) {
        ChannelSummaryTopicSection topTopics = buildTopTopics(questions);

        List<SupportQuestion> followUpQuestions = questions.stream()
                .filter(question -> question.status() == SupportQuestionStatus.UNANSWERED
                        || question.status() == SupportQuestionStatus.AI_LOW_CONFIDENCE)
                .sorted(Comparator.comparing(SupportQuestion::createdAt).reversed())
                .toList();

        List<String> followUpBodies = followUpQuestions.stream()
                .map(SupportQuestion::body)
                .limit(MAX_FOLLOW_UP_QUESTIONS)
                .toList();

        ChannelSummaryFollowUpsSection unansweredFollowUps = new ChannelSummaryFollowUpsSection(
                followUpQuestions.size(),
                followUpQuestions.isEmpty()
                        ? "No unanswered follow-ups in this window."
                        : followUpQuestions.size() + " question"
                                + (followUpQuestions.size() == 1 ? " is" : "s are")
                                + " awaiting further input or clarification from the community or instructors.",
                followUpBodies
        );

        List<ApprovedFaq> approvedFaqs = approvedFaqRepository.findByCourseId(courseId).stream()
                .filter(faq -> isWithinWindow(faq.updatedAt(), windowStart, windowEnd))
                .sorted(Comparator.comparing(ApprovedFaq::updatedAt).reversed())
                .limit(MAX_TEXT_ITEMS)
                .toList();

        ChannelSummaryTextItemsSection keyDecisions = new ChannelSummaryTextItemsSection(
                approvedFaqs.isEmpty()
                        ? "No approved FAQ decisions recorded yet for this course."
                        : "Instructor-approved answers that set direction for the cohort.",
                approvedFaqs.stream().map(ApprovedFaq::answer).limit(MAX_TEXT_ITEMS).toList()
        );

        ChannelSummaryTextItemsSection resourceLinks = new ChannelSummaryTextItemsSection(
                approvedFaqs.isEmpty()
                        ? "No curated resources linked from approved FAQs yet."
                        : "Approved FAQ entries that learners can revisit as reference material.",
                approvedFaqs.stream().map(ApprovedFaq::question).limit(MAX_TEXT_ITEMS).toList()
        );

        return new ChannelSummaryDigest(topTopics, unansweredFollowUps, keyDecisions, resourceLinks);
    }

    private ChannelSummaryTopicSection buildTopTopics(List<SupportQuestion> questions) {
        if (questions.isEmpty()) {
            return new ChannelSummaryTopicSection(
                    "No activity yet",
                    "Post support questions in this channel to populate topic insights."
            );
        }

        List<FaqCandidateGroup> groups = faqCandidateGrouper.group(questions);
        if (!groups.isEmpty()) {
            String title = groups.stream()
                    .sorted(Comparator.comparingInt(group -> -group.supportQuestions().size()))
                    .limit(3)
                    .map(FaqCandidateGroup::representativeQuestion)
                    .map(this::shortTopicLabel)
                    .collect(Collectors.joining(", "));
            return new ChannelSummaryTopicSection(
                    title,
                    "Most conversations clustered around recurring themes in this channel."
            );
        }

        List<String> topTokens = topSignificantTokens(questions, 3);
        String title = String.join(", ", topTokens);
        if (title.isBlank()) {
            title = shortTopicLabel(questions.getFirst().body());
        }

        return new ChannelSummaryTopicSection(
                title,
                "Most conversations were around the themes learners asked about in this window."
        );
    }

    private String shortTopicLabel(String question) {
        String normalized = question.trim();
        if (normalized.length() <= 48) {
            return normalized;
        }
        return normalized.substring(0, 45) + "...";
    }

    private List<String> topSignificantTokens(List<SupportQuestion> questions, int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (SupportQuestion question : questions) {
            for (String token : FaqCandidateGrouper.significantTokens(question.body())) {
                counts.merge(token, 1, Integer::sum);
            }
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(entry -> capitalize(entry.getKey()))
                .toList();
    }

    private static String capitalize(String token) {
        if (token.isEmpty()) {
            return token;
        }
        return token.substring(0, 1).toUpperCase(Locale.ROOT) + token.substring(1);
    }

    private List<ChannelSummaryTimelineEvent> buildTimeline(
            UUID courseId,
            List<SupportQuestion> questions,
            List<ChannelMessage> messages,
            Instant windowStart,
            Instant windowEnd
    ) {
        List<ChannelSummaryTimelineEvent> events = new ArrayList<>();

        for (SupportQuestion question : questions) {
            events.add(new ChannelSummaryTimelineEvent(
                    "NEW_QUESTION",
                    question.body(),
                    question.createdAt()
            ));
            if (question.status() == SupportQuestionStatus.AI_ANSWERED) {
                events.add(new ChannelSummaryTimelineEvent(
                        "QUESTION_RESOLVED",
                        question.body(),
                        question.createdAt()
                ));
            }
        }

        for (ChannelMessage message : messages) {
            events.add(new ChannelSummaryTimelineEvent(
                    "REPLY_ADDED",
                    message.body(),
                    message.createdAt()
            ));
        }

        for (ApprovedFaq approvedFaq : approvedFaqRepository.findByCourseId(courseId)) {
            if (!isWithinWindow(approvedFaq.updatedAt(), windowStart, windowEnd)) {
                continue;
            }
            events.add(new ChannelSummaryTimelineEvent(
                    "KEY_DECISION",
                    approvedFaq.question(),
                    approvedFaq.updatedAt()
            ));
        }

        return events.stream()
                .sorted(Comparator.comparing(ChannelSummaryTimelineEvent::occurredAt).reversed())
                .limit(MAX_TIMELINE_EVENTS)
                .toList();
    }
}
