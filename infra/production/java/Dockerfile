ARG JDK_IMAGE
ARG JRE_IMAGE
FROM ${JDK_IMAGE} AS extract
ARG MODULE
WORKDIR /work
COPY backend/${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar input.jar
COPY infra/production/java/Probe.java infra/production/java/Migrate.java helpers/
RUN jar -xf input.jar && mkdir -p app/helpers && \
    mv BOOT-INF/classes app/classes && mv BOOT-INF/lib app/lib && \
    sed -n 's/^Start-Class: //p' META-INF/MANIFEST.MF | tr -d '\r' > app/main-class && \
    test -s app/main-class && javac -d app/helpers helpers/Probe.java && \
    if find app/lib -name 'flyway-core-*.jar' | grep -q .; then javac -cp 'app/lib/*' -d app/helpers helpers/Migrate.java; fi

FROM ${JRE_IMAGE}
WORKDIR /app
RUN groupadd --gid 10001 chanter && useradd --uid 10001 --gid 10001 --no-create-home chanter && \
    mkdir /app/resources /app/media-spool && chown 10001:10001 /app/resources /app/media-spool && \
    chmod 700 /app/resources /app/media-spool
COPY --from=extract --chown=10001:10001 /work/app/ /app/
COPY infra/production/java/entrypoint.sh /app/entrypoint.sh
RUN chmod 755 /app/entrypoint.sh
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
