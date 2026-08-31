FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY service-a-gateway/target/service-a-gateway-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
