# ==================== Stage 1: Build ====================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy build descriptors first for layer caching
COPY pom.xml .
COPY src ./src

# Build the application JAR
RUN mvn -B clean package -DskipTests

# ==================== Stage 2: Runtime ====================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/langchain4j_sister_project-0.0.1-SNAPSHOT.jar app.jar

# Security: run as non-root user
USER nobody:nobody

# Expose application port
EXPOSE 8080

# Health check for container orchestration
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM flags: ZGC for low-latency, memory-ratio-based heap, secure random source
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
