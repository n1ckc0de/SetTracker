# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# curl is used by HEALTHCHECK; keep the image lean otherwise.
RUN apk add --no-cache curl \
    && addgroup -S gymsync \
    && adduser -S gymsync -G gymsync

USER gymsync

COPY --from=builder /app/target/gymsync-api-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
