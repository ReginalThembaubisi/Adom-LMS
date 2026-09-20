# Stage 1: Build the frontend (outputs to src/main/resources/static per vite.config.js)
FROM node:22-slim AS frontend-build
WORKDIR /repo
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN cd frontend && npm ci
COPY frontend ./frontend
RUN cd frontend && npm run build

# Stage 2: Build the Maven application package
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY --from=frontend-build /repo/src/main/resources/static ./src/main/resources/static
RUN mvn clean package -DskipTests

# Stage 3: Deploy packaged application jar inside a lightweight JRE
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# TieredStopAtLevel=1 keeps the JIT at C1 only, skipping C2's background compilation --
# background compiler threads competing with the main thread for CPU is a real cost on a
# constrained free-tier instance during the one CPU-bound stretch that matters (bean/Hibernate
# bootstrap), and C2's payoff (faster steady-state throughput after warmup) isn't worth it for a
# process that spends the bulk of its life idle between requests rather than running hot loops.
# CICompilerCount=1 keeps that to a single compiler thread instead of the default two, so there
# is one fewer thread contending with the main thread for whatever CPU share this instance gets.
ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:CICompilerCount=1", "-jar", "app.jar"]
