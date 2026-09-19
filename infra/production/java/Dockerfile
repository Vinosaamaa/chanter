ARG JDK_IMAGE
ARG JRE_IMAGE
FROM ${JDK_IMAGE} AS extract
ARG MODULE
WORKDIR /work
ADD --checksum=sha256:bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.31.1/opentelemetry-javaagent.jar /work/telemetry/agent.jar
COPY backend/telemetry/target/telemetry-0.1.0-SNAPSHOT.jar /work/telemetry/privacy.jar
COPY backend/${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar input.jar
COPY infra/production/java/Probe.java infra/production/java/Migrate.java helpers/
COPY backend/agent-service/target/embedding-model/ model-source/
RUN jar -xf input.jar && mkdir -p app/helpers && \
    mv BOOT-INF/classes app/classes && mv BOOT-INF/lib app/lib && \
    sed -n 's/^Start-Class: //p' META-INF/MANIFEST.MF | tr -d '\r' > app/main-class && \
    test -s app/main-class && javac -d app/helpers helpers/Probe.java && \
    if find app/lib -name 'flyway-core-*.jar' | grep -q .; then javac -cp 'app/lib/*' -d app/helpers helpers/Migrate.java; fi && \
    if [ "$MODULE" = agent-service ]; then mkdir -p app/models/minilm && cp model-source/* app/models/minilm/; fi

FROM ${JRE_IMAGE}
WORKDIR /app
RUN groupadd --gid 10001 chanter && useradd --uid 10001 --gid 10001 --no-create-home chanter && \
    mkdir /app/resources /app/media-spool && chown 10001:10001 /app/resources /app/media-spool && \
    chmod 700 /app/resources /app/media-spool
COPY --from=extract --chown=10001:10001 /work/app/ /app/
COPY --from=extract --chown=10001:10001 /work/telemetry/ /app/telemetry/
COPY infra/production/java/entrypoint.sh /app/entrypoint.sh
RUN chmod 755 /app/entrypoint.sh
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
