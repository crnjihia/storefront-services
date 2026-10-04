# Multi-stage Dockerfile for Catalog Service
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY catalog-service/src catalog-service/src
COPY common-events/pom.xml common-events/pom.xml
COPY common-events/src common-events/src
RUN mvn -pl catalog-service -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/catalog-service/target/catalog-service-0.0.1-SNAPSHOT.jar app.jar
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
EXPOSE 8082
ENTRYPOINT ["java","-jar","app.jar"]
