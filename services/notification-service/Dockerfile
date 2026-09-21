# Сборка одного сервиса из монорепозитория. Контекст сборки — корень репозитория:
#   docker build -f services/notification-service/Dockerfile -t notification-service .
# На Railway: переменная RAILWAY_DOCKERFILE_PATH=services/notification-service/Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY common ./common
COPY services ./services
RUN mvn -B -q -DskipTests -pl services/notification-service -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/services/notification-service/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# порт задаёт хостинг через PORT, см. application.yml
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
