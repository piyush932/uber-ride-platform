# FINAL STEP (Docker). Builds any module: docker build --build-arg MODULE=auth-service .
# -am also builds the modules it depends on (entity-service)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN mvn -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
