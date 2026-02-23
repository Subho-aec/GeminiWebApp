# ═══════════════════════════════════════════════════════════════════════
#  MedBot — Multi-stage Docker Build
#  Stage 1: Build with Maven    → produces the JAR
#  Stage 2: Run with slim JRE   → minimal final image (~200MB vs ~800MB)
# ═══════════════════════════════════════════════════════════════════════

# ─── Stage 1: Build ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

# ─── Stage 2: Runtime ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8084

ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
