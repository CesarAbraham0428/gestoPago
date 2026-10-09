FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

# The wrapper is tracked without its executable bit; restore it inside the image.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon clean bootJar \
    && app_jar="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "$app_jar" \
    && cp "$app_jar" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
ENV PORT=10000
EXPOSE 10000

COPY --from=build --chown=10001:0 /workspace/app.jar /app/app.jar

USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
