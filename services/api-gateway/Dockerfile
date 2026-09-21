# Сборка одного сервиса из монорепозитория. Контекст сборки — корень репозитория:
#   docker build -f services/api-gateway/Dockerfile -t api-gateway .
# На Railway: переменная RAILWAY_DOCKERFILE_PATH=services/api-gateway/Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY common ./common
COPY services ./services
RUN mvn -B -q -DskipTests -pl services/api-gateway -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/services/api-gateway/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# порт задаёт хостинг через PORT, см. application.yml
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
