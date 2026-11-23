# API Spring Boot (Java 17). Contexto de build: esta carpeta (backend/).
#   docker build -t gestopago-api backend
#   docker run --env-file backend/.env -p 8080:8080 gestopago-api

# --- Compilación ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Primero el wrapper y los scripts de Gradle, para que la descarga de dependencias
# quede en caché mientras no cambien (se invalida solo si cambia build.gradle).
COPY gradlew settings.gradle build.gradle lombok.config ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*.jar app.jar

# --- Ejecución -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --no-create-home --uid 10001 apiuser
COPY --from=build --chown=apiuser /app/app.jar app.jar
USER apiuser

# Render (y la mayoría de plataformas) inyecta PORT; en local queda 8080.
# Si no se define DB_URL, se arma con DB_HOST / DB_PORT / DB_NAME (así lo entrega render.yaml).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "export DB_URL=\"${DB_URL:-jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME}}\"; exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
