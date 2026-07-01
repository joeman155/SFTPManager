# ── Stage 1: Build ──
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml FIRST and download dependencies in isolation
# This layer is cached unless pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Now copy source and build
# Only this layer reruns when src/ changes
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /app/target/sftp-manager-1.0.0.war app.war
RUN chown appuser:appgroup app.war
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.war"]

