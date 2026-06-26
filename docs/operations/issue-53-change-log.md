# Issue #53 Change Log

Issue: [#53 Production Course Resources Panel](https://github.com/Vinosaamaa/chanter/issues/53)

## Summary

Shipped the production `#resources` course-channel experience: resource list with search and type filters, instructor upload (multipart + AI-approved toggle), learner download/preview, AI-approved badges, and enrollment-gated access messaging. Generic course channels keep the #51 live-chat path; `#resources` uses a dedicated center-column panel.

## Frontend

| Path | Purpose |
|------|---------|
| `features/resources/course-resource-types.ts` | Shared resource + access types |
| `features/resources/course-resource-format.ts` | File kind, size formatting, filter/search helpers |
| `features/resources/course-resources-api.ts` | List, upload, download, resource-access APIs |
| `features/shell/hooks/use-course-resources-channel.ts` | Load access + resources, upload/download state |
| `features/shell/components/ResourcesChannelConversation.tsx` | `#resources` panel UI (header, filters, grid cards) |
| `features/shell/components/ChannelConversation.tsx` | Routes `#resources` to `ResourcesChannelGate` |
| `features/shell/shell-routes.ts` | `isResourcesChannel` helper |
| `lib/api-client.ts` | `apiFetchBlob` for authenticated binary downloads |
| `features/shell/components/ContextPlaceholder.tsx` | Remove stale #52/#53 placeholder copy |

## Deferred (out of scope)

- Week-module folder hierarchy from mockup (backend exposes flat list only).
- Dedicated preview endpoint (PDF preview opens blob URL; other types download).

## Verification

```bash
cd frontend && npm run lint && npm run build
```

Browser (local stack):

1. `/dev/demo` — create course, upload resource as instructor, list as enrolled learner.
2. Production shell — open `#resources` as instructor (upload + badges) and as non-enrolled user (access denied banner).
