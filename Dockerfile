FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

# Let the JVM size its heap from the container's cgroup memory limit (IRD-002/IRD-018) instead
# of the host's total RAM. Set here (not application.yml) so it applies to every `java` invocation.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8083

USER nobody

# Liveness for orchestrators + satisfies Trivy DS-0026. Hits the service's own
# GET /health (direct 200, no auth). wget ships with the alpine busybox base.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8083/health || exit 1
CMD ["java", "-jar", "app.jar"]

