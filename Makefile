.PHONY: infra-up infra-down infra-logs backend-build backend-test backend-gateway backend-auth backend-community backend-message backend-realtime backend-media backend-agent backend-analytics backend-search backend-notification frontend-install frontend-dev frontend-build verify setup-git-hooks product-up product-supervise product-down product-health product-test product-demo-seed product-cleanup-demo-servers product-e2e product-e2e-critical

ifeq ($(shell uname -s),Darwin)
JAVA_HOME_21 := $(shell /usr/libexec/java_home -v 21 2>/dev/null)
JAVA_HOME_23 := $(shell /usr/libexec/java_home -v 23 2>/dev/null)
ifneq ($(JAVA_HOME_21),)
export JAVA_HOME := $(JAVA_HOME_21)
else ifneq ($(JAVA_HOME_23),)
export JAVA_HOME := $(JAVA_HOME_23)
else
$(error Java 21 or 23 is required on macOS. Install one and rerun this target)
endif
endif

ifneq (,$(wildcard .env))
include .env
export
endif

define require-jwt-secret
	@test -n "$$CHANTER_JWT_SECRET" || (echo "CHANTER_JWT_SECRET is required. Copy .env.example to .env and set a 32+ character secret." && exit 1)
	@test $${#CHANTER_JWT_SECRET} -ge 32 || (echo "CHANTER_JWT_SECRET must be at least 32 characters." && exit 1)
endef

define require-internal-service-token
	@test -n "$$CHANTER_INTERNAL_SERVICE_TOKEN" || (echo "CHANTER_INTERNAL_SERVICE_TOKEN is required. Add a 32+ character value to .env." && exit 1)
	@test $${#CHANTER_INTERNAL_SERVICE_TOKEN} -ge 32 || (echo "CHANTER_INTERNAL_SERVICE_TOKEN must be at least 32 characters." && exit 1)
endef

infra-up:
	@test -f .env || cp .env.example .env
	docker compose -f infra/docker-compose.yml --env-file .env up -d postgres redis redpanda minio

infra-down:
	docker compose -f infra/docker-compose.yml down

infra-logs:
	docker compose -f infra/docker-compose.yml logs -f

backend-build:
	cd backend && mvn -B -q package -DskipTests

backend-test:
	cd backend && mvn -B -q test

backend-gateway:
	$(require-jwt-secret)
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl gateway-service spring-boot:run

backend-auth:
	$(require-jwt-secret)
	$(require-internal-service-token)
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl auth-service spring-boot:run

backend-community:
	$(require-jwt-secret)
	$(require-internal-service-token)
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl community-service spring-boot:run

backend-message:
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl message-service spring-boot:run

backend-realtime:
	$(require-jwt-secret)
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl realtime-service spring-boot:run

backend-media:
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl media-service spring-boot:run

backend-agent:
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl agent-service spring-boot:run

backend-analytics:
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl analytics-service spring-boot:run

backend-search:
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl search-service spring-boot:run

backend-notification:
	$(require-internal-service-token)
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl notification-service spring-boot:run

frontend-install:
	cd frontend && npm install

frontend-dev:
	cd frontend && npm run dev

frontend-build:
	cd frontend && npm run build

verify: backend-test frontend-build product-test

product-up:
	./scripts/product/up.sh

product-down:
	./scripts/product/down.sh

product-supervise:
	./scripts/product/supervise.sh

product-health:
	./scripts/product/health.sh

product-test:
	./scripts/product/lib.test.sh

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
