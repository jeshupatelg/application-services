# Use a lightweight JRE 21 Alpine image for execution
FROM eclipse-temurin:21-jre-alpine

# Install curl inside the container for advanced debugging and health checks
RUN apk add --no-cache curl

# Set non-root system user for improved container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set working directory inside the container
WORKDIR /app

# Copy the built Spring Boot runnable JAR into the container
# This expects the context to be the repository root
COPY app/common/target/portfolio-orchestrator.jar app.jar

# Create shared static folder and ensure correct non-root permissions
RUN mkdir -p /app/shared && chown -R appuser:appgroup /app /app/shared

# Switch to the non-root user
USER appuser

# Expose the default Spring Boot port
EXPOSE 8080

# Environment configurations for JVM optimization
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+UseStringDeduplication"

# Define the entrypoint to run the Spring Boot application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
