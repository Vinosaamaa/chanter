# Issue 248 / PR 317: review remediation

## Round 1

Base reviewed head: 249185a37318701f6c1f1b4a04388d8631e98a30. Independent coordinator review and CodeAnt full review were both read before this remediation.

- Coordinator: preserve the CHANTER_LLM_ENABLED deployment kill switch. A populated disabled catalog incorrectly exposed choices and clients. Regression first observed local as the effective default instead of source-only. Fixed default, listing, selection, definition and client paths; test proves no provider probe or HTTP request while disabled. Enabled integration fixtures now opt in explicitly.
- CodeAnt: classify Ollama's in-band error frame as provider unavailability. Regression first observed INVALID_RESPONSE; fixed to UNAVAILABLE without exposing its diagnostic.
- CodeAnt: authorized saved answers must remain replayable after a model is removed or a selector changes. Regression first observed a rejected replay. Saved-answer authorization now precedes new-generation selection validation; test proves the same saved answer ID and no second provider call. The UI must display the saved audit provider/model, not the current selector.
- CodeAnt documentation: distinguish unknown provider usage from known zero before an attempt. Kept the safe zero settlement for a reservation abandoned before transport, added a no-provider-call regression, and clarified the design. Unknown post-attempt usage still retains the reservation.

The initial hosted head passed backend, dependency review, Engineering policy and CodeAnt quality gates. Frontend failed on the existing Browserslist dependency audit; product E2E failed pulling the removed minio/minio image. Those launch baseline repairs belong to #312; neither gate was weakened or skipped here. Final remediation-head verification is recorded on the PR.

Local remediation verification passed: common 13, agent-service 67, gateway-service 36 tests, zero failures/errors; 116 total. All five protocol evaluation cases passed.

## Round 2

Reviewed heads: 45cd190c2ce0b839a66e04ed27065cd146374e34 and its clean rebase 2600e5fb344d7691f9fdfd10dd59606f5effb1cc on merged main d9c68b56b8ef85e65a057d858a7331002a0d4a08. The rebased head passed all five functional hosted jobs, including product E2E: [CI run](https://github.com/Vinosaamaa/chanter/actions/runs/34675682202). New-head checks and full review remain required after these fixes.

- Duplicate generation: reproduced a second reservation in the concurrent settlement/persistence gap and after an abandoned reservation crossed the daily budget reset. Every possible provider attempt now retains the question claim; only explicit NOT_STARTED settlement releases it. No database lock is held during provider I/O. Tests also prove pre-provider release and source-only recovery without a second provider call; the API explains the uncertain result.
- Staff answer reads: instructors already see installation grants, but production TA scopes expose channel access without learner enrollment, hiding resource grants in filtered presence. Reproduced the TA 403, then changed evidence checks to use actual installation grants after channel authorization, intersected with the viewer's approved resources and content access. Both staff roles can read; removed approval still denies TA and learner reads.
- Ignored Ollama constructor parameter: removed the unused connect-timeout argument. The shared five-second connection timeout remains bounded by the overall execution deadline.
- Oversized Ollama output: reproduced acceptance of oversized UTF-8 content with a small reported eval_count. Streaming and completion now enforce an independent visible-payload limit before accepting content. This limit is not a tokenizer or proof of upstream billing.
- Deletion boundary requested by the coordinator: a removed catalog resource cannot publish a stored citation or download old content. This is a contract regression, not a full #244 deletion lifecycle test. Strict AVAILABLE filtering will be integrated after #318 supplies the status field; unknown statuses must then fail closed.

Affected-module verification plus the final focused deletion check passed 122 tests: common 13, agent-service 73, gateway-service 36, zero failures/errors. The five protocol evaluation cases remain separate from semantic tutor evaluation. No paid provider calls, model downloads or native subscription sessions were used.
