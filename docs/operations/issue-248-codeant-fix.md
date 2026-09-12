# Issue 248 / PR 317: review remediation

## Round 1

Base reviewed head: 249185a37318701f6c1f1b4a04388d8631e98a30. Independent coordinator review and CodeAnt full review were both read before this remediation.

- Coordinator: preserve the CHANTER_LLM_ENABLED deployment kill switch. A populated disabled catalog incorrectly exposed choices and clients. Regression first observed local as the effective default instead of source-only. Fixed default, listing, selection, definition and client paths; test proves no provider probe or HTTP request while disabled. Enabled integration fixtures now opt in explicitly.
- CodeAnt: classify Ollama's in-band error frame as provider unavailability. Regression first observed INVALID_RESPONSE; fixed to UNAVAILABLE without exposing its diagnostic.
- CodeAnt: authorized saved answers must remain replayable after a model is removed or a selector changes. Regression first observed a rejected replay. Saved-answer authorization now precedes new-generation selection validation; test proves the same saved answer ID and no second provider call. The UI must display the saved audit provider/model, not the current selector.
- CodeAnt documentation: distinguish unknown provider usage from known zero before an attempt. Kept the safe zero settlement for a reservation abandoned before transport, added a no-provider-call regression, and clarified the design. Unknown post-attempt usage still retains the reservation.

The initial hosted head passed backend, dependency review, Engineering policy and CodeAnt quality gates. Frontend failed on the existing Browserslist dependency audit; product E2E failed pulling the removed minio/minio image. Those launch baseline repairs belong to #312; neither gate was weakened or skipped here. Final remediation-head verification is recorded on the PR.

Local remediation verification passed: common 13, agent-service 67, gateway-service 36 tests, zero failures/errors; 116 total. All five protocol evaluation cases passed.
