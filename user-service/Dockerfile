# Multi-stage Dockerfile for User Service
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY user-service/pom.xml user-service/pom.xml
COPY user-service/src user-service/src
COPY common-events/pom.xml common-events/pom.xml
COPY common-events/src common-events/src
RUN mvn -pl user-service -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/user-service/target/user-service-0.0.1-SNAPSHOT.jar app.jar
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
