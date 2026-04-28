# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon -x test -x integrationTest

FROM eclipse-temurin:21-jre
WORKDIR /app
# bootJar's archiveFileName is pinned to app.jar in build.gradle.kts so this
# COPY is a single deterministic source file (no wildcard).
COPY --from=build /workspace/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
