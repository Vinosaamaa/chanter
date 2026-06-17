# community-service

Owns Study Server community state for the education MVP.

Implemented slice:

- `POST /api/v1/study-servers` creates a Study Server, persists the Owner role, and creates default Study Server Channels.
- `GET /api/v1/study-servers/{id}` returns the created Study Server shell.

Local port: `8082`.
