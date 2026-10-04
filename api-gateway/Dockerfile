# Multi-stage Dockerfile for API Gateway
# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY api-gateway/src api-gateway/src
# Copy shared modules needed for compilation
COPY common-events/pom.xml common-events/pom.xml
COPY common-events/src common-events/src
RUN mvn -pl api-gateway -am -DskipTests clean package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
# Non-root user
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
