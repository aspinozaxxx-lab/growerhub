# Диаграммы потоков

        ## Ручной полив: последовательность событий
        `mermaid
        sequenceDiagram
          participant User as Пользователь
          participant Web as Веб-интерфейс
          participant FastAPI as FastAPI
          participant MQTT as MQTT брокер
          participant ESP as ESP32
          participant DB as База данных
          User->>Web: Нажимает «Старт полива»
          Web->>FastAPI: POST /api/manual-watering/start
          FastAPI->>MQTT: Publish pump.start (QoS1)
          MQTT-->>ESP: Доставляет команду
          ESP-->>MQTT: Ack accepted + state running
          MQTT-->>FastAPI: Ack/state сообщения
          FastAPI->>DB: Обновляет shadow и журналы
          FastAPI-->>Web: Статус и ACK для UI
          ESP-->>FastAPI: POST /api/device/{id}/status (телеметрия)
        `

        ## Поток телеметрии
        `mermaid
        flowchart TD
          subgraph Device
            Sensors[Датчики
(влажность, DHT22)]
            Actuators[Реле насоса и света]
            Firmware[Прошивка
Application.cpp]
          end
          subgraph Cloud
            API[FastAPI
/api/device/...]
            DB[(PostgreSQL)]
            Shadow[DeviceShadowStore]
            UI[SPA manual_watering.html]
          end
          Sensors -->|замеры| Firmware
          Actuators --> Firmware
          Firmware -->|HTTP POST| API
          API --> DB
          API --> Shadow
          Shadow --> UI
          UI -->|polling| API
        `

        Диаграммы помогают увидеть, что MQTT используется только для команд/ACK, а телеметрия и настройки идут по HTTP.
