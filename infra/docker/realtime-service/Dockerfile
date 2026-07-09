FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Parent POM lists every backend module; copy the full tree so Maven can resolve the reactor.
COPY backend backend
RUN cd backend && mvn -B -q -pl realtime-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system chanter && useradd --system --gid chanter --home-dir /app chanter
COPY --from=build /workspace/backend/realtime-service/target/realtime-service-*.jar /app/app.jar
RUN chown -R chanter:chanter /app
USER chanter
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
