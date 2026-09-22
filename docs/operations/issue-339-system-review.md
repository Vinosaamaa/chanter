# System review: integrated product interaction (#339)

The owning behavior is browser navigation. No backend service, schema, credential or authorization contract changes. Focus follows the currently visible pane, using current item identity rather than private content in a selector. The last-item fallback keeps the list accessible when a completed notification disappears. Initial list loads do not steal focus.

The native Add friend dialog replaces manual keyboard handling and prevents background interaction. Search and request authorization continue through the existing hooks. Browser cancellation closes only this dialog. Cross-browser keyboard and screenshot evidence is required before acceptance; component tests alone cannot prove native focus containment or visual layout.

Hosted real product tests add Firefox and WebKit alongside Chromium. They preserve one worker, deterministic seeded resources and the existing authenticated artifact restrictions. Tests run on isolated CI services, not user accounts or production. The full final capability union is still required after lifecycle and recovery merge. Fixture evidence cannot stand in for real service delivery, real audio devices, live model-provider login or production hosting.

Remaining acceptance: manual screen-reader/browser-zoom review; production-like measured performance; actual provider configuration; final legal and retention review; complete recovery and public cutover under their owning issues. No public launch claim is made.
