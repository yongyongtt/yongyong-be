# Multi-stage: build with JDK, run with JRE only
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Server has 2GB RAM: one Gradle worker, no daemon, capped heap
ENV GRADLE_OPTS="-Xmx512m -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=1"

# Download dependencies in a separate layer so code changes don't refetch them
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# Tests run in CI (Testcontainers can't run inside docker build)
RUN ./gradlew bootJar -x test --no-daemon \
    && cp "$(ls build/libs/*.jar | grep -v plain)" app.jar

FROM eclipse-temurin:21-jre-alpine
LABEL com.yongyong.project=yongyong
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
