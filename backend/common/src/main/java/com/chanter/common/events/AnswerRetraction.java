package com.chanter.common.events;

import java.util.UUID;

/** Exact saved-answer removal. A later revision supersedes a claimed acceptance on the same question aggregate. */
public record AnswerRetraction(UUID answerId,UUID questionId,UUID channelId,UUID authorId) {
    public static final String KIND="ANSWER_RETRACTED";
    public static final String NOTIFICATION_KIND="ANSWER_NOTIFICATION_RETRACTED";
    public static final String SOURCE_TYPE="STUDY_ASSISTANT_ANSWER";
    public static final String RECEIPT_KIND="ANSWER_RECONCILED";
    public AnswerRetraction {
        if(answerId==null || questionId==null || channelId==null || authorId==null) throw new IllegalArgumentException("Invalid answer retraction");
    }
    public String aggregateKey() { return AcceptedAnswerStatus.KIND+":"+questionId; }
    public String notificationKey() { return NotificationEventWriter.aggregateKey(authorId,SOURCE_TYPE,answerId,"SUPPORT_QUESTION_ANSWERED"); }
    public String receiptKey() { return RECEIPT_KIND+":"+answerId; }
}
