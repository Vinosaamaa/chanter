package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestionReply;
import java.util.List;
import java.util.UUID;

public interface SupportQuestionReplyRepository {

    SupportQuestionReply save(SupportQuestionReply reply);

    List<SupportQuestionReply> findBySupportQuestionId(UUID supportQuestionId);
}
