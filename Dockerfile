# Build stage
FROM maven:3.9.8-eclipse-temurin-17 AS build
WORKDIR /workspace/app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package spring-boot:repackage

# Runtime stage (small JRE 17)
FROM eclipse-temurin:17-jdk
WORKDIR /app

# JVM flags: respect memory limits + open modules for Drools 7
ENV JAVA_TOOL_OPTIONS=""

COPY --from=build /workspace/app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]