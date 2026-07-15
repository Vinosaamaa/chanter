# Issue #102 — Production auth

## Summary

Email verification (env-gated), password reset, forgot-password UI on `/sign-in`, Google SSO when configured, auth endpoint rate limits, and email provider config (`log` default).

### Backend
- Flyway `V2__production_auth.sql` — `email_verified`, email tokens, oauth accounts
- Endpoints: `/forgot-password`, `/reset-password`, `/verify-email`, `/oauth/providers`, `/oauth/google/callback`
- `CHANTER_AUTH_REQUIRE_EMAIL_VERIFICATION` defaults **false** so local demo seed keeps working

### Frontend
- Forgot / reset / verify-email pages
- Google button enables when providers API returns Google
- OAuth callback route `/oauth/callback/google`

### Staging walkthrough
1. Set `CHANTER_PUBLIC_BASE_URL` + `CHANTER_AUTH_REQUIRE_EMAIL_VERIFICATION=true`
2. Register → open verify link from email (or insert a token via `auth_email_tokens` for log-provider staging) → `/verify-email?token=` → sign in
3. Forgot password → reset link → `/reset-password?token=` → sign in

Note: the `log` email provider does **not** print raw tokens (SAST). For local/staging without SMTP, generate/use tokens via the smoke test pattern or an ops SQL insert of a hashed token.

### Tests
`ProductionAuthSmokeTest`, SignInPage forgot-password link assertion.
