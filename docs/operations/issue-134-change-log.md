# Issue #134 - change log

**Branch:** `feature/134-truthful-course-resources-ai-install`
**Commit:** `feat(ui-v2): #134 make course resources and AI install truthful`

## Goal

Replace the Course Resources mock data and simulated AI installer with durable Course Resource APIs, real Study Assistant grants, and honest loading, access, empty, error, and installed states.

## What changed

### Durable resource library

- Removed the hard-coded Week 1/Week 2 resource catalog.
- Renders only resources returned by the Media Service-backed Course Resource API.
- Keeps search and type filters live against the loaded collection.
- Classifies audio/video uploads as recordings without mislabeling arbitrary files.
- Clears prior Course resources and access capabilities before loading a new Course so stale permissions cannot leak across navigation.

```tsx
const filteredResources = resources.filter(
  (resource) =>
    matchesResourceFilter(resource, activeFilter) &&
    matchesResourceSearch(resource, searchQuery),
)
```

### Resource upload and retrieval

- Added an accessible upload dialog with explicit title and opt-in AI approval.
- Enforces the backend 10 MB limit before upload.
- Keeps upload failures visible inside the active dialog.
- Supports separate PDF preview and file download actions with progress feedback.
- Added Escape handling, focus containment, and focus restoration for the upload dialog.

```tsx
const uploaded = await onUpload(file, {
  title: title.trim() || undefined,
  aiApproved,
})
if (uploaded) onClose()
```

### Real Study Assistant installation

- Reads installed state from the backend presence query.
- Reuses `StudyAssistantInstallDialog` instead of presenting simulated capability checkboxes.
- Sends the selected Study Server channel, Course, Cohort, Course channel, and AI-approved resource grant keys through the existing install flow.
- Replaces the install action with a truthful active state after installation.

```tsx
{assistantQuery.data?.installed ? (
  <span className="resource-assistant-status active">
    <Sparkles />
    AI Study Assistant · Active
  </span>
) : null}
```

## TDD coverage

Red/green coverage includes:

- durable empty, loading, denied, and failed resource states;
- resource search, filters, PDF preview, and download dispatch;
- explicit upload metadata and AI approval;
- client-side rejection above 10 MB;
- upload errors inside the modal and Escape dismissal;
- stale Course data/capability clearing after a subsequent request failure;
- recording classification;
- backend-derived installed state and real grant selection.

## Browser and service verification

- `make product-health` passed after a clean product restart.
- Signed in through the running app and created a disposable Study Server, Course, and Cohort.
- Uploaded `Issue 134 Recursion Notes`; the persisted row showed its real size and AI-approved state.
- Verified search, filtering, and download behavior in the UI.
- Opened the real Study Assistant grant dialog, excluded `#study-room`, installed the assistant, and verified backend presence contained the selected Course/resource/channel grants but not the excluded channel.
- The Resources page then rendered `AI Study Assistant · Active` with no duplicate install action.

## Architecture and docs

No system-design or architecture contract changed. This slice connects the v2 page to the existing Course Resource and Study Assistant grant boundaries, so only live workflow/status documentation was updated.

## Verification

```text
cd frontend && npm run lint
cd frontend && npm run test
cd frontend && npm run build
git diff --check
make product-health
```

The Vite build retains the existing informational large-chunk warning.
