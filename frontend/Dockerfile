# App web Flutter servida con nginx. Contexto de build: esta carpeta (frontend/).
#   docker build -t gestopago-web --build-arg API_BASE_URL=https://mi-api.onrender.com frontend
#   docker run -p 8081:8080 gestopago-web
#
# API_BASE_URL se usa dos veces: se compila dentro de la app (--dart-define) y se
# inyecta en la cabecera CSP (connect-src) de nginx al arrancar el contenedor.

# --- Compilación ---------------------------------------------------------------------------
FROM debian:bookworm-slim AS build

# Flutter fijado a la versión con la que se desarrolla (pubspec exige Dart ^3.13.4).
ARG FLUTTER_VERSION=3.47.8
RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl unzip xz-utils ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch ${FLUTTER_VERSION} https://github.com/flutter/flutter.git /flutter
# El build de Render no deja a tar (como root) restaurar propietarios al extraer los artefactos de Flutter.
RUN mv /usr/bin/tar /usr/bin/tar.real \
    && printf '#!/bin/sh\nexec /usr/bin/tar.real --no-same-owner "$@"\n' > /usr/bin/tar \
    && chmod +x /usr/bin/tar
ENV PATH="/flutter/bin:${PATH}"
RUN git config --global --add safe.directory /flutter \
    && flutter config --no-analytics --enable-web

WORKDIR /app

COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get

COPY . .
ARG API_BASE_URL=http://localhost:8080
RUN flutter build web -t lib/main_web.dart --release --no-web-resources-cdn \
    --dart-define=API_BASE_URL=${API_BASE_URL}

# --- Servidor ------------------------------------------------------------------------------
FROM nginx:1.27-alpine
ARG API_BASE_URL=http://localhost:8080
ENV API_BASE_URL=${API_BASE_URL}
# Render inyecta PORT; en local queda 8080. nginx sustituye ${PORT} y ${API_BASE_URL} de la plantilla.
ENV PORT=8080

COPY deploy/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/build/web /usr/share/nginx/html

EXPOSE 8080
