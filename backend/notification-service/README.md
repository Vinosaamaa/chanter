# notification-service

Durable user inbox / notification read model for Chanter.

- Port: `8089` (`NOTIFICATION_PORT`)
- Database: `chanter_notification`
- Public APIs: `/api/v1/me/notifications/**`
- Internal create: `POST /api/v1/internal/notifications` (`X-Chanter-Internal-Service-Token`)

Gateway route: `/api/v1/me/notifications/**` → port `8089` (order `-2`, ahead of community `/api/v1/me/**`).
