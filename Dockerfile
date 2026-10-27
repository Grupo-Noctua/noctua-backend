FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B verify

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system noctua && useradd --system --gid noctua noctua
COPY --from=build /app/target/noctua-backend-0.0.1-SNAPSHOT.jar app.jar
COPY --chown=noctua:noctua database/athena/*.sql /app/config/
RUN chmod 0555 /app/config && chmod 0444 /app/config/*.sql
USER noctua
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
