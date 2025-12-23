# growerhub-backend (Java)

Minimal Spring Boot backend (Java 21) for GrowerHub.

## Windows + VS Code setup

1) Install JDK 21 (Temurin or Oracle).
2) Set `JAVA_HOME` to your JDK folder and add `%JAVA_HOME%\\bin` to `PATH`.
3) Verify:

```bash
java -version
./gradlew -version
```

4) Run tests:

```bash
cd backend
./gradlew test
```

5) Run the app (dev):

```bash
cd backend
./gradlew bootRun
```

## Build jar

Prereqs: Java 21 + Gradle 8.10+.

```bash
cd backend
gradle clean build
```

Jar will be created in `backend/build/libs/`.

## Run locally

```bash
cd backend
java -jar build/libs/growerhub-backend-0.0.1-SNAPSHOT.jar
```

Or run in dev mode:

```bash
cd backend
gradle bootRun
```

## Docker

Build image (from repo root):

```bash
docker build -t growerhub-backend:local backend
```

Run container:

```bash
docker run --rm -p 8080:8080 growerhub-backend:local
```

## Health check

```bash
curl http://localhost:8080/health
```

Expected response:

```json
{"status":"ok"}
```
