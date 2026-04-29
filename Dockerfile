FROM eclipse-temurin:21-jre

WORKDIR /app
ARG JAR_FILE=target/Lab5-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080 8081 8082 8083 9090 9091 9092 9093

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
