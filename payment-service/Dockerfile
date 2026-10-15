FROM eclipse-temurin:21-jre
WORKDIR /app
ARG JAR_FILE=payment-service/target/payment-service-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
EXPOSE 8084
ENTRYPOINT ["java","-jar","app.jar"]
