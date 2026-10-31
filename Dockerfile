# ---- build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests
# ---- runtime ----
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system app && \
    useradd --system --gid app app
COPY --from=build --chown=app:app \
    /app/target/order-service-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]