# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy build configuration first and warm Gradle's dependency cache in its own
# layer. Editing src/ does not invalidate this layer, so iterative rebuilds
# skip dependency downloads on cache hit. Only build.gradle.kts /
# settings.gradle.kts / gradle/ changes invalidate it.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies --quiet

# Now bring in sources and produce the runnable jar.
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test -x integrationTest

FROM eclipse-temurin:21-jre
WORKDIR /app
# bootJar's archiveFileName is pinned to app.jar in build.gradle.kts so this
# COPY is a single deterministic source file (no wildcard).
COPY --from=build /workspace/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
