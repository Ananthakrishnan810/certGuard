# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package && cp target/certguard-backend-*.jar /app.jar

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 certguard
ENV TZ=Asia/Kolkata
COPY --from=build /app.jar /app/app.jar
USER certguard
EXPOSE 8188
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
