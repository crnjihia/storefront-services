# Multi-stage Dockerfile for Payment Service
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY payment-service/pom.xml payment-service/pom.xml
COPY payment-service/src payment-service/src
COPY common-events/pom.xml common-events/pom.xml
COPY common-events/src common-events/src
RUN mvn -pl payment-service -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/payment-service/target/payment-service-0.0.1-SNAPSHOT.jar app.jar
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
EXPOSE 8084
ENTRYPOINT ["java","-jar","app.jar"]
