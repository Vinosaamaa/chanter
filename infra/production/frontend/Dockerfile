ARG CADDY_IMAGE
ARG GO_IMAGE
FROM ${GO_IMAGE} AS build
WORKDIR /src
COPY infra/production/frontend/caddy/ ./
RUN GOTOOLCHAIN=local go mod download && GOTOOLCHAIN=local go mod verify \
    && GOTOOLCHAIN=local go mod vendor \
    && GOTOOLCHAIN=local go run -mod=readonly ./compat \
    && CGO_ENABLED=0 GOTOOLCHAIN=local go test -mod=vendor ./... \
    && CGO_ENABLED=0 GOTOOLCHAIN=local go test -mod=vendor github.com/caddyserver/caddy/v2/modules/caddyhttp -run '^TestMatchExpression' \
    && CGO_ENABLED=0 GOTOOLCHAIN=local go build -mod=vendor -trimpath -ldflags='-s -w' -o /caddy .

FROM ${CADDY_IMAGE}
# Build from locked patched dependencies, without privileged-port capabilities.
RUN rm /usr/bin/caddy && mkdir -p /srv /data/caddy /config/caddy && chown -R 10001:10001 /srv /data /config
COPY --from=build /caddy /usr/bin/caddy
COPY --chown=10001:10001 frontend/dist/ /srv/
RUN test -z "$(find /srv -type f -name '*.map' -print -quit)"
COPY infra/production/frontend/Caddyfile /etc/caddy/Caddyfile
USER 10001:10001
EXPOSE 8080 8443
