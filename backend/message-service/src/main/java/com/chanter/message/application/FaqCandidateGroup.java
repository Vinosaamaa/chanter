package com.chanter.message.application;

import com.chanter.message.domain.SupportQuestion;
import java.util.List;

public record FaqCandidateGroup(
        String representativeQuestion,
        List<SupportQuestion> supportQuestions
) {
}
