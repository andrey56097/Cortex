# ============================================
# Stage 1: Build — compile the application
# ============================================
FROM gradle:8.10.2-jdk17 AS build

WORKDIR /app

# Copy build config first (caches dependencies)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

# Copy source code
COPY src ./src

# Build the executable JAR
RUN ./gradlew bootJar --no-daemon

# ============================================
# Stage 2: Runtime — run the application
# ============================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Create non-root user for security
RUN groupadd --system spring && useradd --system --gid spring spring

# Copy only the JAR from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Switch to non-root user
USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
