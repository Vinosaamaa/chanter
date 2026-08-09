.PHONY: infra-up infra-down infra-logs backend-build backend-test backend-verify backend-gateway backend-auth backend-community backend-message backend-realtime backend-media backend-agent backend-analytics backend-search backend-notification frontend-install frontend-dev frontend-build verify setup-git-hooks product-env product-up product-supervise product-down product-health product-test product-demo-seed product-cleanup-demo-servers product-e2e product-e2e-critical

JAVA21 := ./scripts/java21.sh
HERMETIC_TEST := ./scripts/testing/run-hermetic.sh
MAVEN_BACKEND := mvn -s backend/.mvn/settings.xml -f backend/pom.xml

ifneq (,$(wildcard .env))
include .env
export
endif

define require-jwt-secret
	@test -n "$$CHANTER_JWT_SECRET" || (echo "CHANTER_JWT_SECRET is required. Run: make product-env" && exit 1)
	@test $${#CHANTER_JWT_SECRET} -ge 32 || (echo "CHANTER_JWT_SECRET must be at least 32 characters." && exit 1)
	@test "$$CHANTER_JWT_SECRET" != "chanter-local-dev-jwt-secret-32bytes!!" || (echo "CHANTER_JWT_SECRET rejects known default value (SEC-04). Run: make product-env" && exit 1)
endef

define require-internal-service-token
	@test -n "$$CHANTER_INTERNAL_SERVICE_TOKEN" || (echo "CHANTER_INTERNAL_SERVICE_TOKEN is required. Run: make product-env" && exit 1)
	@test $${#CHANTER_INTERNAL_SERVICE_TOKEN} -ge 32 || (echo "CHANTER_INTERNAL_SERVICE_TOKEN must be at least 32 characters." && exit 1)
	@test "$$CHANTER_INTERNAL_SERVICE_TOKEN" != "chanter-local-dev-internal-service-token-32bytes!!" || (echo "CHANTER_INTERNAL_SERVICE_TOKEN rejects known default value (SEC-04). Run: make product-env" && exit 1)
endef

define require-infra-secrets
	@test -n "$$REDIS_PASSWORD" || (echo "REDIS_PASSWORD is required (SEC-12). Run: make product-env" && exit 1)
	@test -n "$$MINIO_ROOT_USER" || (echo "MINIO_ROOT_USER is required (SEC-12). Run: make product-env" && exit 1)
	@test -n "$$MINIO_ROOT_PASSWORD" || (echo "MINIO_ROOT_PASSWORD is required (SEC-12). Run: make product-env" && exit 1)
	@test -n "$$LIVEKIT_API_KEY" || (echo "LIVEKIT_API_KEY is required (SEC-12). Run: make product-env" && exit 1)
	@test -n "$$LIVEKIT_API_SECRET" || (echo "LIVEKIT_API_SECRET is required (SEC-12). Run: make product-env" && exit 1)
endef

infra-up:
	@test -f .env || (echo "Missing .env — run: make product-env" && exit 1)
	@$(require-jwt-secret)
	@$(require-infra-secrets)
	docker compose -f infra/docker-compose.yml --env-file .env up -d postgres redis redpanda minio

infra-down:
	docker compose -f infra/docker-compose.yml down

infra-logs:
	docker compose -f infra/docker-compose.yml logs -f

backend-build:
	$(JAVA21) $(MAVEN_BACKEND) -B -q package -DskipTests

backend-test:
	$(HERMETIC_TEST) $(JAVA21) $(MAVEN_BACKEND) -B -q test

backend-verify:
	$(HERMETIC_TEST) $(JAVA21) $(MAVEN_BACKEND) -B -q verify

backend-gateway:
	$(require-jwt-secret)
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl gateway-service spring-boot:run

backend-auth:
	$(require-jwt-secret)
	$(require-internal-service-token)
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl auth-service spring-boot:run

backend-community:
	$(require-jwt-secret)
	$(require-internal-service-token)
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl community-service spring-boot:run

backend-message:
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl message-service spring-boot:run

backend-realtime:
	$(require-jwt-secret)
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl realtime-service spring-boot:run

backend-media:
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl media-service spring-boot:run

backend-agent:
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl agent-service spring-boot:run

backend-analytics:
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl analytics-service spring-boot:run

backend-search:
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl search-service spring-boot:run

backend-notification:
	$(require-internal-service-token)
	$(JAVA21) $(MAVEN_BACKEND) -B -q install -DskipTests
	$(JAVA21) $(MAVEN_BACKEND) -B -q -pl notification-service spring-boot:run

frontend-install:
	cd frontend && npm install

frontend-dev:
	cd frontend && npm run dev

frontend-build:
	cd frontend && npm run build

verify: backend-verify frontend-build product-test

product-env:
	chmod +x ./scripts/product/init-env.sh
	./scripts/product/init-env.sh

product-up:
	$(JAVA21) ./scripts/product/up.sh

product-down:
	./scripts/product/down.sh

product-supervise:
	$(JAVA21) ./scripts/product/supervise.sh

product-health:
	./scripts/product/health.sh

product-test:
	$(HERMETIC_TEST) ./scripts/product/lib.test.sh
	$(HERMETIC_TEST) ./scripts/testing/run-hermetic.test.sh
	./scripts/java21.test.sh

product-demo-seed: product-health
	DEMO_PASSWORD="$${DEMO_PASSWORD:-chanter-dev-demo}" ./scripts/seed-workable-product-demo.sh

product-cleanup-demo-servers: product-health
	DEMO_PASSWORD="$${DEMO_PASSWORD:-chanter-dev-demo}" ./scripts/cleanup-duplicate-demo-servers.sh

product-e2e-critical:
	chmod +x ./scripts/product/e2e.sh
	./scripts/product/e2e.sh critical

product-e2e:
	chmod +x ./scripts/product/e2e.sh
	./scripts/product/e2e.sh product

setup-git-hooks:
	./scripts/setup-git-hooks.sh
