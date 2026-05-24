FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /workspace/target/*.jar /app/app.jar

RUN mkdir -p /app/logs && chown -R app:app /app

USER app

EXPOSE 8080

ENTRYPOINT ["/bin/sh", "-c", "export SERVER_PORT=${PORT:-8080}; exec java ${JAVA_OPTS:-} -jar /app/app.jar"]