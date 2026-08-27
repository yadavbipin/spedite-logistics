# Render deployment image: Angular frontend + Spring Boot API in one web service.
# PostgreSQL is provided separately by Render.
FROM node:22-alpine AS frontend-build
WORKDIR /build/frontend

COPY spedite-frontend/package.json spedite-frontend/package-lock.json ./
RUN npm ci

COPY spedite-frontend/ ./
RUN npm run build

FROM eclipse-temurin:17-jdk-jammy AS backend-build
WORKDIR /build/backend

COPY spedite-logistics/.mvn .mvn
COPY spedite-logistics/mvnw spedite-logistics/pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY spedite-logistics/src src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends nginx gettext-base \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /etc/nginx/sites-enabled/default

COPY --from=frontend-build /build/frontend/dist/spedite-frontend/browser /usr/share/nginx/html
COPY --from=backend-build /build/backend/target/spedite-logistics-0.0.1-SNAPSHOT.jar /app/app.jar
COPY deployment/render-nginx.conf.template /app/nginx.conf.template
COPY deployment/render-entrypoint.sh /app/render-entrypoint.sh

RUN chmod +x /app/render-entrypoint.sh

EXPOSE 10000
ENTRYPOINT ["/app/render-entrypoint.sh"]
