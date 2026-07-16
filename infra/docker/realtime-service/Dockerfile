FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
# Parent POM lists every backend module; copy module POMs for reactor resolution plus sources we build.
COPY backend/pom.xml backend/pom.xml
COPY backend/common backend/common
COPY backend/realtime-service backend/realtime-service
COPY backend/gateway-service/pom.xml backend/gateway-service/pom.xml
COPY backend/auth-service/pom.xml backend/auth-service/pom.xml
COPY backend/community-service/pom.xml backend/community-service/pom.xml
COPY backend/message-service/pom.xml backend/message-service/pom.xml
COPY backend/media-service/pom.xml backend/media-service/pom.xml
COPY backend/agent-service/pom.xml backend/agent-service/pom.xml
COPY backend/analytics-service/pom.xml backend/analytics-service/pom.xml
COPY backend/search-service/pom.xml backend/search-service/pom.xml
RUN cd backend && mvn -B -q -pl realtime-service -am package -DskipTests

FROM eclipse-temurin:21.0.11_10-jre
WORKDIR /app
RUN groupadd --system chanter && useradd --system --gid chanter --home-dir /app chanter
COPY --from=build /workspace/backend/realtime-service/target/realtime-service-*.jar /app/app.jar
RUN chown -R chanter:chanter /app
USER chanter
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
