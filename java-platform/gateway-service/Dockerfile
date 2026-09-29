FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/gateway-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
