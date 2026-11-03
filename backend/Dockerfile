FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# The application needs no privileges: run it as an unprivileged user.
RUN groupadd --system wunderlore && useradd --system --gid wunderlore --no-create-home wunderlore
COPY --from=build --chown=wunderlore:wunderlore /app/target/*.jar app.jar
USER wunderlore

# Documentation only: the real port is server.port=${PORT:8080}. Render sets PORT itself
# (10000 by default) and detects it, so this does not need to match.
EXPOSE 8080

# Sized for small containers such as Render Free (512 MB, shared CPU): the JVM default heap
# is only 25% of container memory, which is tight for Spring Boot, and the serial collector
# avoids extra GC threads and memory overhead. Heap is a percentage, not a fixed size, so
# larger plans and Docker Compose are not capped.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-jar", "app.jar"]
