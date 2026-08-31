FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY service-c-analytics/target/service-c-analytics-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
