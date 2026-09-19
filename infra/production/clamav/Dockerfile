ARG SIGNATURE_IMAGE
ARG ALPINE_IMAGE
FROM ${SIGNATURE_IMAGE} AS signatures

FROM ${ALPINE_IMAGE}
# Distribution packages supply native x86_64 and aarch64 builds of the supported LTS patch.
RUN apk add --no-cache clamav-daemon=1.4.6-r0 freshclam=1.4.6-r0 clamav-libunrar=1.4.6-r0 netcat-openbsd ca-certificates && \
    deluser clamav && addgroup -g 10001 chanter-media && \
    adduser -S -D -H -u 10002 -G chanter-media clamav && \
    mkdir -p /var/lib/clamav /run/clamav && \
    chown 10002:10001 /var/lib/clamav /run/clamav && \
    chmod 700 /var/lib/clamav && chmod 2770 /run/clamav
# Copy signature data only. No executable or library from the Debian image enters this runtime.
COPY --from=signatures --chown=10002:10001 /var/lib/clamav/ /var/lib/clamav/
COPY infra/production/clamav/clamd.conf infra/production/clamav/freshclam.conf /etc/clamav/
COPY infra/production/clamav/entrypoint.sh /usr/local/bin/chanter-scanner
RUN chmod 755 /usr/local/bin/chanter-scanner && chmod 700 /var/lib/clamav
USER 10002:10001
ENTRYPOINT ["/usr/local/bin/chanter-scanner"]
