ARG CADDY_IMAGE
FROM ${CADDY_IMAGE}
# The upstream binary carries a bind capability. Remove it because this image
# uses unprivileged ports and the runtime drops every Linux capability.
RUN setcap -r /usr/bin/caddy && mkdir -p /srv /data/caddy /config/caddy && chown -R 10001:10001 /srv /data /config
COPY --chown=10001:10001 frontend/dist/ /srv/
COPY infra/production/frontend/Caddyfile /etc/caddy/Caddyfile
USER 10001:10001
EXPOSE 8080 8443
