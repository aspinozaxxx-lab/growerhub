# GrowerHub

![Firmware CI](https://github.com/aspinozaxxx-lab/growerhub/actions/workflows/ci-firmware.yml/badge.svg)
![Backend CI/CD](https://github.com/aspinozaxxx-lab/growerhub/actions/workflows/ci-cd-backend.yml/badge.svg)
![Frontend Deploy](https://github.com/aspinozaxxx-lab/growerhub/actions/workflows/deploy-frontend.yml/badge.svg)

GrowerHub - система ухода за растениями: устройство публикует состояние через MQTT, backend хранит данные и управляет командами, frontend показывает состояние и администрирование.

## Состав

- `backend` - Java Spring Boot backend, REST API, MQTT, домены и миграции БД.
- `frontend` - React/Vite приложение.
- `firmware` - прошивка устройства на C++ для PlatformIO.
- `ansible` - серверная инфраструктура и роли деплоя.
- `docs/architecture` - архитектурная документация.
- `.github/workflows` - CI/CD текущих частей проекта.

## Основные команды

Backend:

```bash
cd backend
./gradlew test
./gradlew bootRun
```

Frontend:

```bash
cd frontend
npm install
npm run build
```

Firmware:

```bash
cd firmware
pio test
pio run
```

Ansible:

```bash
cd ansible
make java-backend
make nginx
make mosquitto
```

## Архитектура

Главный архитектурный документ: `docs/architecture/architecture.md`.
