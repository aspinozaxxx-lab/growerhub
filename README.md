🌱 Smart Watering System
MVP Системы выращивания
Состав MVP:
* Фунциональные возможности:
* Продукт в корпусе с разьемами (3д печать)
* Упаковка: коробка со стикером
* Аппаратная часть:
(БАЗА)
** мини насос,
** реле 1 шт,
** датчик влажности почвы на кабеле с разьемом rj11
** розетка для датчика влажности почвы rj11
** кабель питания
(ПЛЮС)
** реле 2 шт
** розетка на корпусе (для подсветки)
* Программная часть:
** Сервер FastAPI для логики
** Веб-сервер
** Firmware для контроллера

Ближайшие задачи:
 Сделать рабочий прототип
купить домен и настроить его на сервер
Настройка nginx как reverse proxy + SSL
Настройка мониторинга и логирования
 Собрать кабельный датчик влажности
 Проверить как поливает с компенсированной капельницей
 Проверить что датчик почвы не подключен постоянно к электроэнергии
 Напечатать корпус для датчика влажности
 Собрать макет на дощечке
 Назначать deviceID от мак адреса
 Удалить старое устройство. Сделать кнопку удаления на сайте
 Логи о деплоях выводить
 Отдельную страничку для логов создать
 Разобраться откуда берется температура -273 градуса. Почему не видит датчик
 Сделать только ручной полив
 Настроить пуш через vs code

 На подумать
 Возможность установки разной мощности насосов и работы от магистрального давления
 Нужен ли гроубокс? Экономия на свете, не палевно. Можно перенести в мастерскую
 Как группировать устройства, по пользователям, по помещениям
 Может быть несколько зон у одного пользователя. По насосу на зону или клапана?
 Как добавлять и удалять устройства

Большие планы:
Развитие веб интерфейса:
  Авторизация пользователей, личный кабинет со своими растениями и датчиками
  Добавление веб страницы проекта с описанием и полезной информацией для пользователей и покупателей
Добавление механики автополива
Добавление журнала действий - дневник выращивания
Графики состояний и действий (почва, воздух, поливы)

**Описание текущей системы:**
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


