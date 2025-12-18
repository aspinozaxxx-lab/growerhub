# growerhub-backend (Java)

Minimal Spring Boot backend (Java 21) for GrowerHub.

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

