# =====================================================================
#  Noctua Backend - build multi-stage
#  Estagio 1 compila e roda os testes com Gradle + JDK 21.
#  Estagio 2 executa apenas a JRE sobre o jar.
# =====================================================================

# ---------------------------------------------------------------------
# Estagio 1 - build
# ---------------------------------------------------------------------
FROM gradle:8.10.2-jdk21 AS build

WORKDIR /workspace

# Copia primeiro apenas os manifestos para reaproveitar a camada de cache
# de dependencias enquanto o codigo-fonte muda.
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src/ src/

# Executa a suite completa. O build falha se qualquer teste quebrar.
# `bootJar` nao depende de `test`, por isso `test` e explicito aqui.
RUN gradle --no-daemon clean test bootJar

# ---------------------------------------------------------------------
# Estagio 2 - runtime
# ---------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl e necessario para o HEALTHCHECK
RUN apk add --no-cache curl \
 && addgroup -S noctua \
 && adduser -S -G noctua noctua

WORKDIR /app
COPY --from=build /workspace/build/libs/noctua-backend.jar app.jar
RUN chown -R noctua:noctua /app

USER noctua

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=10 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
