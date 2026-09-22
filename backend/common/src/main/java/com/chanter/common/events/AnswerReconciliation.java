package com.chanter.common.events;

/** Message reports status reconciliation; notification then proves its own ordered removal. */
public record AnswerReconciliation(AnswerRetraction answer,String state) {
    public AnswerReconciliation {
        if(answer==null || !"COMPLETE".equals(state))
            throw new IllegalArgumentException("Invalid answer reconciliation");
    }
}
