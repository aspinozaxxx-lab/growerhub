🌱 Smart Watering System
🎯 Контекстный якорь (обновляется каждый день)
ТЕКУЩЕЕ СОСТОЯНИЕ (06.10.2024)
ESP32: Автономное время (RTC+NTP), EEPROM, DHT22, 2 реле (полив+свет)

Сервер: FastAPI + PostgreSQL + автозапуск systemd

Веб: Отображение температуры/влажности, управление настройками

Работает: Автоматический полив, управление освещением, OTA

Авто-деплой: GitHub webhook + systemd сервисы

АРХИТЕКТУРА
ESP32: C++/PlatformIO, пины: 34-почва, 4-полив, 5-свет, 15-DHT22

Сервер: Python/FastAPI, порт 8000, IP: 192.168.0.11

База: PostgreSQL, Docker

Сети: WiFi JR/qazwsxedc

Деплой: Ubuntu + systemd + авто-обновление

СИСТЕМНЫЕ СЕРВИСЫ
bash
smart-watering-system.service      # Основное приложение
watering-deploy-agent.service      # Авто-деплой агент

ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ
GITHUB_TOKEN # GitHub API токен

ПОСЛЕДНИЕ ИЗМЕНЕНИЯ
✅ Добавлено автономное время и сохранение настроек в EEPROM

✅ Подключен датчик DHT22 (температура/влажность воздуха)

✅ Инвертированная логика реле для нормально-разомкнутых контактов

✅ Веб-интерфейс показывает все данные сенсоров

✅ Рефакторинг сервера завершен + авто-деплой система

✅ Настроен poll-агент для автоматического обновления

СЛЕДУЮЩИЕ ШАГИ (07.10.2024)
~~Рефакторинг проекта - модульная структура~~

~~Создание GitHub репозитория~~

~~ООП реструктуризация кода ESP32~~

Настройка nginx как reverse proxy + SSL

Настройка мониторинга и логирования

Оптимизация производительности

📁 Структура проекта
text
smart-watering-system/
├── firmware/           # ESP32 прошивка
├── server/             # FastAPI сервер
├── web/                # Веб-интерфейс  
├── docs/               # Документация
├── scripts/            # Вспомогательные скрипты
├── deploy.sh           # Скрипт деплоя
└── deploy_agent.py     # Агент авто-обновления
🔧 Авто-деплой система
Сервисы systemd:
smart-watering-system.service - основное приложение

watering-deploy-agent.service - мониторинг GitHub и авто-обновление

Конфигурация:
Интервал проверки: 1 минута

Ветка: main

Аутентификация: GitHub Personal Access Token

Логирование: journalctl

Быстрый старт
bash
# Сервер
cd server && python3 main.py

# ESP32  
cd firmware && pio run --target upload

# Мониторинг сервисов
sudo systemctl status smart-watering-system watering-deploy-agent

# Логи деплой агента
sudo journalctl -u watering-deploy-agent.service -f
🔐 Безопасность
GitHub токен хранится в переменных окружения systemd сервиса:

bash
sudo systemctl edit watering-deploy-agent.service
[Service]
Environment=GITHUB_TOKEN=" secret token "
📞 Контакты

Сервер: http://192.168.0.11:8000

Device ID: esp32_01

Репозиторий: https://github.com/aspinozaxxx-lab/smart-watering-system

🎯 Current Status - STABLE
✅ What's Working:
ESP32 OOP Architecture - Complete modular refactoring

Sensor Reading - DHT22 (temperature/humidity) and Soil Moisture

Actuator Control - Relays for water pump and grow light

Server Communication - HTTP API to FastAPI backend

Autonomous Operation - Works offline with settings persistence

System Monitoring - Memory, uptime, diagnostics

Auto-deployment - GitHub monitoring with authenticated API

🔧 Technical Stack:
ESP32 with PlatformIO

C++ OOP with proper inheritance and composition

FastAPI backend on 192.168.0.11:8000

PostgreSQL database

WiFi + OTA updates support

Systemd services for auto-start

GitHub API for auto-deployment

📁 Module Structure:
firmware/src/
├── Sensors/ # Sensor abstractions
├── Actuators/ # Relay and pump control
├── Network/ # WiFi, OTA, HTTP
├── System/ # Settings, tasks, monitoring
└── Application.h/cpp # Main system coordinator

🚀 Next Milestone: Advanced Features
Web interface enhancements

Mobile app integration

Advanced scheduling

Data analytics

SSL reverse proxy with nginx
