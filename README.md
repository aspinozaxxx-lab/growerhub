# 🌱 Smart Watering System

## 🎯 Контекстный якорь (обновляется каждый день)

### ТЕКУЩЕЕ СОСТОЯНИЕ (05.10.2024)
- **ESP32**: Автономное время (RTC+NTP), EEPROM, DHT22, 2 реле (полив+свет)
- **Сервер**: FastAPI + PostgreSQL + автозапуск systemd
- **Веб**: Отображение температуры/влажности, управление настройками
- **Работает**: Автоматический полив, управление освещением, OTA

### АРХИТЕКТУРА
- ESP32: C++/PlatformIO, пины: 34-почва, 4-полив, 5-свет, 15-DHT22
- Сервер: Python/FastAPI, порт 8000, IP: 192.168.0.11
- База: PostgreSQL, Docker
- Сети: WiFi JR/qazwsxedc

### ПОСЛЕДНИЕ ИЗМЕНЕНИЯ
- ✅ Добавлено автономное время и сохранение настроек в EEPROM
- ✅ Подключен датчик DHT22 (температура/влажность воздуха)
- ✅ Инвертированная логика реле для нормально-разомкнутых контактов
- ✅ Веб-интерфейс показывает все данные сенсоров

### СЛЕДУЮЩИЕ ШАГИ (06.10.2024)
1. Рефакторинг проекта - модульная структура
2. Создание GitHub репозитория
3. ООП реструктуризация кода ESP32
4. Написание unit-тестов

## 📁 Структура проекта
smart-watering-system/
├── firmware/ # ESP32 прошивка
├── server/ # FastAPI сервер
├── web/ # Веб-интерфейс
├── docs/ # Документация
└── scripts/ # Вспомогательные скрипты

## 🔧 Быстрый старт
# Сервер
cd server && python3 main.py
# ESP32
cd firmware && pio run --target upload

📞 Контакты
Сервер: http://192.168.0.11
Device ID: esp32_01

## 🎯 Current Status - STABLE

### ✅ What's Working:
- **ESP32 OOP Architecture** - Complete modular refactoring
- **Sensor Reading** - DHT22 (temperature/humidity) and Soil Moisture  
- **Actuator Control** - Relays for water pump and grow light
- **Server Communication** - HTTP API to FastAPI backend
- **Autonomous Operation** - Works offline with settings persistence
- **System Monitoring** - Memory, uptime, diagnostics

### 🔧 Technical Stack:
- **ESP32** with PlatformIO
- **C++ OOP** with proper inheritance and composition
- **FastAPI** backend on 192.168.0.11:8000
- **PostgreSQL** database
- **WiFi + OTA** updates support

### 📁 Module Structure:
firmware/src/
├── Sensors/ # Sensor abstractions
├── Actuators/ # Relay and pump control
├── Network/ # WiFi, OTA, HTTP
├── System/ # Settings, tasks, monitoring
└── Application.h/cpp # Main system coordinator

## 🚀 Next Milestone: Advanced Features
- Web interface enhancements
- Mobile app integration  
- Advanced scheduling
- Data analytics
