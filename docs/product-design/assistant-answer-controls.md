# Assistant answer controls and recovery

Owner: [#321](https://github.com/Vinosaamaa/chanter/issues/321), child of #248. Base: merged main db480a3. Lane: Questions API, state, presentation, tests and evidence. Branch: `codex/321-ai-answer-ui`. Delivery: one issue-linked PR; root owns merge. #316 native subscription access is outside this change.

## Design before implementation

Keep the learning-desk system from #254: paper `#FFFFFF`, desk `#F3F6FA`, ink `#192C46`, quiet ink `#596A80`, action blue `#2458D3`, rule `#DCE3EC`. Instrument Sans remains the single interface family. Reading text stays left aligned and bounded; native selects and actions have at least 44px touch targets and 16px phone input text.

Place a compact answer setup below the selected learner question and before its answer. It belongs to this question, rather than a global settings page. Show the authorized model and answer type with a brief capability/cost explanation. Use the existing reading canvas without another decorative card. On phones the fields stack inside the detail pane; the support-question composer remains separate at the bottom.

```text
Selected support question

Answer source [Approved sources             v]
Answer type   [Source only                  v]
Find approved Course passages. No provider charge.
[Find sources]

During work: Finding approved sources…
Completed: saved answer + its actual provider/model + citations
Interrupted: clear message + [Use approved sources]
```

The source-only catalog entry is not a paid model. Quoted evidence selects approved passages; it must not claim to explain or synthesize the Course. Grounded explanation is visibly unavailable with a plain reason. For API models, explain the returned billing category and that a chat subscription does not cover separately billed API access. Local compute and source-only results must not inherit that notice.

The completed answer owns its attribution. Current selectors cannot relabel a saved/replayed answer. Only authoritative completion supplies final text and citations. Interim text is a draft; error, cancellation or EOF before completion discards it. A possible prior generation offers Source only recovery or existing human support, never an automatic or disguised second provider generation.

## Self-review

Two ordinary labeled controls fit the Course workflow better than model logos, pricing cards or an AI-themed sidebar. The memorable Course typography remains the main visual element. Descriptions explain a decision the learner can make; raw error codes, configuration variables and backend implementation details stay out of the product flow. The model/type combination must stay valid as available options change, and empty/forbidden catalogs must leave the original support question usable.

## System boundary and verification plan

The authenticated API client retains per-account guards on every stream read. The Questions transport parses catalog data and SSE status/token/complete/error events, rejects premature EOF and releases readers on every exit. The Questions state owner accepts results only for the initiating account, channel, question and current request. Presentation renders catalog capabilities, safe recovery and saved answer metadata.

Observe focused failures for incomplete streams, structured errors, superseded requests and metadata replay before fixing them. Verify the real source-only service journey separately from explicit provider/error fixtures. Capture phone, tablet and desktop answer/recovery views, keyboard behavior and source links without authenticated traces or secret-bearing artifacts. Final CI, complete review, merged-main and release receipts remain required; this plan is not a completion certificate.
