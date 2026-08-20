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
ENTRYPOINT ["java", "-jar", "app.jar"]
