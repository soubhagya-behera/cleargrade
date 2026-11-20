FROM maven:3.9.14-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S cleargrade && adduser -S -G cleargrade cleargrade

COPY --from=build /workspace/target/cleargrade-backend-*.jar /app/app.jar
RUN chown -R cleargrade:cleargrade /app

USER cleargrade
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=12 --start-period=40s \
  CMD wget -q -O - http://localhost:8080/actuator/health >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
