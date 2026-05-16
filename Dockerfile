FROM node:22-alpine AS frontend-build

WORKDIR /app

COPY frontend/package*.json ./frontend/
RUN cd frontend && npm ci

COPY frontend ./frontend
COPY backend/src/main/resources ./backend/src/main/resources

RUN cd frontend && npm run build

FROM maven:3.9.9-eclipse-temurin-23 AS backend-build

WORKDIR /app/backend

COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend-build /app/backend/src/main/resources/static ./src/main/resources/static

RUN mvn -DskipTests package

FROM eclipse-temurin:23-jre

WORKDIR /app

COPY --from=backend-build /app/backend/target/terminal-indoor-navigation-1.0.0.jar ./app.jar

ENV PORT=10000
EXPOSE 10000

CMD ["sh", "-c", "java -jar app.jar --server.address=0.0.0.0 --server.port=${PORT} --server.ssl.enabled=false"]
