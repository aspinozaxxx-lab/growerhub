FROM gradle:8.10.2-jdk21 AS build

WORKDIR /home/gradle/project

COPY build.gradle settings.gradle ./
COPY src ./src

RUN gradle --no-daemon clean build


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /home/gradle/project/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

