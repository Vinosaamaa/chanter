.PHONY: infra-up infra-down infra-logs backend-build backend-test backend-gateway backend-auth backend-community frontend-install frontend-dev frontend-build verify setup-git-hooks

ifeq ($(shell uname -s),Darwin)
export JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 23 2>/dev/null)
endif

infra-up:
	@test -f .env || cp .env.example .env
	docker compose -f infra/docker-compose.yml --env-file .env up -d

infra-down:
	docker compose -f infra/docker-compose.yml down

infra-logs:
	docker compose -f infra/docker-compose.yml logs -f

backend-build:
	cd backend && mvn -B -q package -DskipTests

backend-test:
	cd backend && mvn -B -q test

backend-gateway:
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl gateway-service spring-boot:run

backend-auth:
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl auth-service spring-boot:run

backend-community:
	cd backend && mvn -B -q install -DskipTests && mvn -B -q -pl community-service spring-boot:run

frontend-install:
	cd frontend && npm install

frontend-dev:
	cd frontend && npm run dev

frontend-build:
	cd frontend && npm run build

verify: backend-test frontend-build

setup-git-hooks:
	./scripts/setup-git-hooks.sh
