# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine@sha256:6ea5548706b60ac0a602eaf48af74792cbab012d90e811ca8db6184b16b5c3d6 AS build

WORKDIR /workspace

# Resolve dependencies before copying source so application changes can reuse
# Docker's dependency layer. The checked-in wrapper pins Maven for every build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699 AS runtime

RUN addgroup -S trademesh \
    && adduser -S -D -H -u 10001 -G trademesh trademesh

WORKDIR /app

COPY --from=build --chown=trademesh:trademesh /workspace/target/trademesh-backend.jar ./trademesh-backend.jar

USER 10001:10001

EXPOSE 8080
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/trademesh-backend.jar"]
