# Multi-stage Dockerfile for Notification Service
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY notification-service/pom.xml notification-service/pom.xml
COPY notification-service/src notification-service/src
COPY common-events/pom.xml common-events/pom.xml
COPY common-events/src common-events/src
RUN mvn -pl notification-service -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/notification-service/target/notification-service-0.0.1-SNAPSHOT.jar app.jar
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
EXPOSE 8085
ENTRYPOINT ["java","-jar","app.jar"]
