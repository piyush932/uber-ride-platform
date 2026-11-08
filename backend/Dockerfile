# syntax=docker/dockerfile:1
# Builds one module: docker build --build-arg MODULE=auth-service backend
# -am also builds the modules it depends on (entity-service). Tests run locally with Maven, not here.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG MODULE
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "app.jar"]
