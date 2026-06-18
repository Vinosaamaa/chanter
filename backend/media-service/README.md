# media-service

Course Resource metadata and local file storage for the Education MVP.

Local port: `8084`.

## APIs

- `POST /api/v1/courses/{courseId}/course-resources` — instructor upload (multipart `file`, `uploaderUserId`, `aiApproved`, optional `title`)
- `GET /api/v1/courses/{courseId}/course-resources?viewerUserId=` — list resources for enrolled learners and instructors
- `GET /api/v1/course-resources/{resourceId}/content?viewerUserId=` — download resource bytes

Authorization is delegated to `community-service` via `GET /api/v1/courses/{courseId}/resource-access`.

## Local run

```bash
make backend-media
```

Requires PostgreSQL database `chanter_media` (see `infra/postgres/init/01-databases.sql`).
