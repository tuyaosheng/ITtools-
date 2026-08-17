# Multi-stage build: Vue SPA -> Spring Boot fat jar (Java 8) -> slim JRE runtime.
#
# BINDING Java 8 adaptation of the (Java 17-style) task brief: the build stage
# uses maven:3.8-eclipse-temurin-8 and the runtime stage uses
# eclipse-temurin:8-jre, matching this project's <java.version>8</java.version>
# / spring-boot-starter-parent 2.7.18 toolchain. Only the node build stage
# stays on a modern Node LTS image.

# ---- Stage 1: build the Vue SPA -------------------------------------------
FROM node:20-alpine AS web
WORKDIR /web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# ---- Stage 2: build the Spring Boot app (SPA embedded as static resources) -
FROM maven:3.8-eclipse-temurin-8 AS build
WORKDIR /src
COPY . .
# Overwrite whatever local (gitignored/.dockerignored) web/dist may or may not
# exist with the fresh build from stage 1. ittools-app/pom.xml's
# maven-resources-plugin copy-spa-dist execution then copies this into
# target/classes/static during generate-resources. -Dskip.frontend.build=true
# skips the frontend-maven-plugin's own npm ci/build (which would otherwise
# re-download Node - unnecessary here and unavailable to do offline anyway
# since this image has no node/npm).
COPY --from=web /web/dist ./web/dist
RUN mvn -q -DskipTests -Dskip.frontend.build=true package

# ---- Stage 3: runtime -------------------------------------------------------
FROM eclipse-temurin:8-jre
COPY --from=build /src/ittools-app/target/ittools-app-*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
