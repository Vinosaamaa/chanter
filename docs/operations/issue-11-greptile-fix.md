# Issue 11 Greptile Fix Log: Monorepo Bootstrap

Date: 2026-06-17  
Branch: `feature/11-monorepo-bootstrap`  
PR: https://github.com/Vinosaamaa/chanter/pull/25  
Final Greptile confidence: `5/5`

## Summary

Greptile reviewed the monorepo bootstrap PR and raised five actionable bootstrap-hardening comments. The final Greptile summary reported that all prior review feedback was addressed before PR #25 merged.

## Fixes Applied

### 1. Docker Compose Credentials Use Environment Variables

Greptile finding:

- `infra/docker-compose.yml` hardcoded Postgres and MinIO credentials even though `.env.example` defined matching variables.
- This could cause backend services to read different credentials than the containers actually used.

Fix:

- Wired Compose credentials through environment variable substitution with local defaults.

Representative snippet:

```yaml
POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-chanter}
MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-chantersecret}
```

### 2. Auth Actuator Health Details Hardened

Greptile finding:

- `auth-service` exposed health details with `show-details: always`.
- Direct hits to port `8081` could reveal component health information without authorization.

Fix:

- Matched Gateway behavior by using `when_authorized`.

Representative snippet:

```yaml
management:
  endpoint:
    health:
      show-details: when_authorized
```

### 3. Auth Controller Test Scoped To Web Layer

Greptile finding:

- `AuthBootstrapControllerTest` used `@SpringBootTest` for a controller-only test.
- That would become slower and more fragile once service/repository dependencies were added.

Fix:

- Replaced it with `@WebMvcTest(AuthBootstrapController.class)`.

Representative snippet:

```java
@WebMvcTest(AuthBootstrapController.class)
class AuthBootstrapControllerTest {
}
```

### 4. Frontend Lint Runs In CI

Greptile finding:

- `package.json` defined a `lint` script, but CI only ran the frontend build.
- ESLint regressions would pass CI silently.

Fix:

- Added a frontend lint step before the frontend build in GitHub Actions.

Representative snippet:

```yaml
- name: Lint frontend
  run: npm run lint
  working-directory: frontend
```

### 5. Makefile Java Home Guarded For macOS

Greptile finding:

- `/usr/libexec/java_home` is macOS-specific.
- Calling it unconditionally could leave Linux developers with an empty `JAVA_HOME`.

Fix:

- Guarded the Java home detection with a Darwin check.

Representative snippet:

```makefile
ifeq ($(shell uname -s),Darwin)
export JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 23 2>/dev/null)
endif
```

## Final Result

- Greptile final summary: `5/5`
- Greptile final status: safe to merge
- PR #25 merged on `main`
