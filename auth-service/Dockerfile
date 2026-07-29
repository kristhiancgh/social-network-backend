# syntax=docker/dockerfile:1.7

# auth-service — el contexto de build es la RAÍZ del repo: Gradle no compila un
# módulo suelto sin settings.gradle.kts ni shared-kernel. docker-compose.yml lo
# fija con `context: .`.

# --- Etapa 1: compilación -----------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY . .

# Quita los retornos de carro: con CRLF el shebang queda "#!/bin/sh\r" y Linux
# busca ese intérprete, fallando con "./gradlew: not found" — engaña, porque el
# que falta es el intérprete. .gitattributes ya lo evita al clonar.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# sharing=locked no es opcional: las cinco imágenes montan la misma caché de
# Gradle y compose las construye en paralelo; sin él fallan por "Timeout waiting
# to lock". -x test porque Testcontainers no puede levantar nada en un build.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :auth-service:bootJar --no-daemon -x test

# --- Etapa 2: ejecución -------------------------------------------------------
# JRE y no JDK: sin compilador ni caché de Gradle. Unos 180 MB en vez de 700.
FROM eclipse-temurin:21-jre-alpine

# Nunca como root: si el proceso se ve comprometido, que no sea dueño del
# sistema de archivos sobre el que corre.
RUN addgroup -S spring && adduser -S -G spring spring

WORKDIR /app
COPY --from=builder --chown=spring:spring /workspace/auth-service/build/libs/app.jar app.jar

USER spring
EXPOSE 8081

# MaxRAMPercentage en vez de -Xmx: la JVM toma el heap del límite del cgroup,
# así que basta con ajustar mem_limit en compose.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD wget -qO- http://localhost:8081/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
