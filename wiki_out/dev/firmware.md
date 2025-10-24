# Прошивка ESP32

        ## Обзор
        - Проект на PlatformIO (platformio.ini), основной файл src/Application.cpp.
        - Стартовая последовательность: egin() → настройка датчиков, актуаторов, сети, задач.
        - Встроенный HTTP-клиент (Network/HTTPClient.cpp) раз в минуту шлёт замеры на /api/device/{id}/status.

        ## Модули
        - Sensors/: SoilMoistureSensor (ADC, калибровка 2800/1300), DHT22Sensor.
        - Actuators/: WaterPump (GPIO4, inverted=true, защита от длительного включения), Relay для света (GPIO5).
        - Network/WiFiManager: WiFiMulti с автопереподключением, хранит до 10 профилей, делает 
econnect() с тремя попытками.
        - Network/HTTPClient: TLS поверх WiFiClientSecure, умеет discoverEndpoints() и логирует ответы.
        - Network/OTAUpdater: готовит устройство к OTA (использует deviceID).
        - System/SettingsManager: SPIFFS конфиг, actoryReset() сбрасывает параметры и поднимает HTTP клиент заново.

        ## Ключевые функции
        - WateringApplication::update() опрашивает сенсоры, следит за подключением Wi-Fi и задачами шедулера.
        - updateActuatorTest() — скриптовый прогон фаз: насос 3 c, затем свет.
        - waterPlants(durationMs) выполняет ручной прогон насоса.
        - checkRelayStates() печатает отладку GPIO, помогает диагностировать инверсию реле.

        ## Пример отправки телеметрии
        `cpp
        bool WateringHTTPClient::sendSensorData(float soil, float temp, float hum,
                                                bool pump, bool light) {
            StaticJsonDocument<512> doc;
            doc["device_id"] = deviceID;
            doc["soil_moisture"] = soil;
            doc["air_temperature"] = temp;
            doc["air_humidity"] = hum;
            doc["is_watering"] = pump;
            doc["is_light_on"] = light;
            doc["timestamp"] = getTimestamp();
            return sendData("/api/device/" + deviceID + "/status", doc);
        }
        `
        > Зачем: сервер фиксирует текущее состояние и строит историю для UI и аналитики.

        ## Сборка и OTA
        - pio run -e <env> — сборка.
        - pio run -t upload -e <env> — прошивка ESP32 через USB.
        - OTA проходит через HTTP-запросы к FastAPI: устройство дергает /api/device/{id}/firmware, скачивает irmware_url и прошивает себя (логика загрузки должна быть реализована на стороне устройства).
