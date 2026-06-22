# Фаза 1: Build (Користимо Maven и JDK да компајлирамо DevBaza код)
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Фаза 2: Runtime (Користимо минималну Alpine слику само за покретање готовог .jar-а)
FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]