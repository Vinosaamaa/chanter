ARG POSTGRES_IMAGE
FROM ${POSTGRES_IMAGE} AS backup-build
USER 0
RUN apk add --no-cache build-base meson ninja pkgconf libpq-dev openssl-dev libxml2-dev lz4-dev zstd-dev bzip2-dev zlib-dev curl
RUN curl --fail --silent --show-error --location \
      https://github.com/pgbackrest/pgbackrest/releases/download/release/2.59.1/pgbackrest-2.59.1.tar.gz -o /tmp/pgbackrest.tar.gz \
    && echo '1cd522afc33b8ff846ef88c55dc238717c9c8817a4f6ca7c9f64887de9c7402d  /tmp/pgbackrest.tar.gz' | sha256sum -c - \
    && tar -xzf /tmp/pgbackrest.tar.gz -C /tmp \
    && meson setup /tmp/build /tmp/pgbackrest-2.59.1 -Dlibssh2=disabled -Dlibsystemd=disabled \
    && ninja -C /tmp/build \
    && /tmp/build/src/pgbackrest version

FROM ${POSTGRES_IMAGE}
COPY --from=backup-build /tmp/build/src/pgbackrest /usr/local/bin/pgbackrest
RUN apk add --no-cache libpq libxml2 lz4-libs zstd-libs libbz2 \
    && mkdir -p /var/lib/pgbackrest/repo \
    && chown 70:70 /var/lib/pgbackrest/repo \
    && pgbackrest version
# Deployment starts PostgreSQL directly as uid 70; the root privilege-switching
# helper is unused and embeds an obsolete Go runtime. Keep it out of the image.
RUN rm /usr/local/bin/gosu
USER 70:70
