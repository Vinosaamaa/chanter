FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/common backend/common
COPY backend/realtime-service backend/realtime-service
RUN cd backend && mvn -B -q -pl realtime-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/realtime-service/target/realtime-service-*.jar /app/app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
