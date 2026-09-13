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

Flyway migracii zapuskayutsya avtomaticheski pri starte prilozheniya.
Dlya lokal'nogo starta nuzhno ukazat' SPRING_DATASOURCE_*.


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
java -jar build/libs/app.jar
```

Or run in dev mode:

```bash
cd backend
gradle bootRun
```

## Environment

Required env for startup:

- `SECRET_KEY`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Optional env:

- `MQTT_HOST`
- `MQTT_PORT`
- `MQTT_USERNAME`
- `MQTT_PASSWORD`
- `MQTT_TLS`

On production server the env file is stored at:

`/opt/growerhub/env/growerhub-java-backend.env`

Production runs as systemd service `growerhub-java-backend.service` from `/opt/growerhub/java-backend/app.jar`.

## Health check

```bash
curl http://localhost:8080/health
```

Expected response:

```json
{"status":"ok"}
```

## Device telemetry

Device telemetry endpoint is unauthenticated:
`POST /api/device/{device_id}/status`


## Изолированная демоферма

Устройство и транспорт защищены согласно
[ADR-006](../docs/architecture/adr/ADR-006-demo-farm-in-shared-application.md)
и [домену demo](../docs/architecture/backend/domains/demo.md).
По умолчанию демо выключено. Миграция V24 маркирует существующие аккаунты
как ACCOUNT, устройства и координаторы как PHYSICAL; она не меняет их владельцев,
сценарии и MQTT credentials.

Параметры включения:

- `DEMO_ENABLED=true`;
- `DEMO_ALLOWED_ORIGIN=https://growerhub.ru` — точный публичный origin;
- `DEMO_SECURE_COOKIE=true` для HTTPS;
- `DEMO_TRUSTED_PROXY_ADDRESSES` — только фактические адреса обратного прокси.
  По умолчанию доверяются loopback-адреса; X-Real-IP от других адресов
  игнорируется. Nginx должен перезаписывать X-Real-IP адресом клиента.

Остальные квоты и интервалы находятся в секции `demo` файла
`application.yml`. Гостевое создание ограничено по хешу адреса клиента;
персональные сохранённые фермы также входят в лимит активных симуляций.

Порядок выпуска:

1. Сделать резервную копию БД и проверить V24 на её отдельной тестовой копии
   без MQTT. Прогнать тесты; проверить вход, сохранение, reset и возврат в аккаунт.
2. Выпустить backend с `DEMO_ENABLED=false`. Сверить `/health`, свежесть
   физической телеметрии и журнал сценариев за два обычных рабочих цикла.
3. Включить демо и проверить отдельную гостевую ферму; после этого выпустить
   frontend с `VITE_DEMO_ENABLED=true`. Проверки не отправляют команды реальной ферме.
4. При проблеме выключить демо и публичную кнопку. Сохранять backend с проверками
   SIMULATED: откат на старый бинарник при оставшихся демоданных недопустим,
   поскольку старые workers не знают об изоляции. Обратное удаление колонок
   и смешанное восстановление дампа не являются процедурой отката.

Для локального предпросмотра используются отдельная БД, пустой `MQTT_HOST`,
локальный `DEMO_ALLOWED_ORIGIN` и `DEMO_SECURE_COOKIE=false`. Пароли,
дампы и данные production для скриншотов не используются.
