ARG POSTGRES_IMAGE
FROM ${POSTGRES_IMAGE} AS build
USER root
RUN apk add --no-cache build-base && \
    wget -q https://codeload.github.com/pgvector/pgvector/tar.gz/refs/tags/v0.8.6 -O /tmp/vector.tar.gz && \
    echo '10bf9938906e5d643bbc4a7eea104b6f57ba4898e5b76b20e60484ea1d5a7f8f  /tmp/vector.tar.gz' | sha256sum -c - && \
    tar -xzf /tmp/vector.tar.gz -C /tmp && cd /tmp/pgvector-0.8.6 && \
    make OPTFLAGS='' with_llvm=no && make install with_llvm=no DESTDIR=/install
FROM ${POSTGRES_IMAGE}
COPY --from=build /install/usr/local/ /usr/local/
