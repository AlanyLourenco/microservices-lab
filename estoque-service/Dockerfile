# syntax=docker/dockerfile:1

# Estágio 1: compila o serviço com Maven. O cache de ~/.m2 é compartilhado entre os
# builds dos três serviços, então as dependências são baixadas uma vez só.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# Estágio 2: imagem final só com a JRE e o .jar (sem Maven nem código-fonte).
FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
