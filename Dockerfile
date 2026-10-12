# ---------------------------------------------------------------
# Stage 1 — Build the fat JAR (Maven + JDK 21)
# ---------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Cache dependency downloads separately from source changes.
COPY pom.xml ./
RUN mvn -q dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -q -DskipTests package

# ---------------------------------------------------------------
# Stage 2 — Minimal JRE runtime image
# ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

# Create a non-root system user to run the application.
# Running as root inside a container is a security anti-pattern.
RUN groupadd --system appgroup && \
    useradd  --system --gid appgroup --no-create-home appuser

WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

# Ensure the non-root user owns the application files.
RUN chown -R appuser:appgroup /app

USER appuser

# JAVA_OPTS allows callers to inject JVM flags at runtime, e.g.:
#   -e JAVA_OPTS="-XX:+UseContainerSupport -Xmx256m"
ENV JAVA_OPTS=""

EXPOSE 8080

# HEALTHCHECK polls /actuator/health every 30 s.
# The health endpoint is exposed and permitted without authentication.
# NOTE: 'wget' is available in the eclipse-temurin:21-jre-jammy base image.
# UNVERIFIED: This Dockerfile has not been built on this machine because Docker is not installed.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
