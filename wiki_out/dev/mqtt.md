# MQTT и протокол ручного полива

        ## Топики и нагрузка
        | Топик | Направление | QoS / Retain | Payload JSON | Описание |
        |-------|-------------|--------------|--------------|----------|
        | gh/dev/{device_id}/cmd | сервер → устройство | QoS1, retain=false | CmdPumpStart / CmdPumpStop | Команды запуска/остановки насоса.
        | gh/dev/{device_id}/ack | устройство → сервер | QoS1, retain=false | Ack | Подтверждение выполнения команды, кэшируется в AckStore.
        | gh/dev/{device_id}/state | устройство → сервер | QoS1, retain=true | DeviceState | Retained состояние ручного полива (статус, длительность, correlation_id).

        ## Форматы сообщений
        `json
        {
          "type": "pump.start",
          "duration_s": 30,
          "correlation_id": "abc123deadbeef",
          "ts": "2025-10-24T10:15:00Z"
        }
        `
        > Зачем: UI получает correlation_id, чтобы отслеживать ACK и прогресс выполнения команды.

        `json
        {
          "correlation_id": "abc123deadbeef",
          "result": "accepted",
          "status": "running",
          "duration_s": 30,
          "started_at": "2025-10-24T10:15:01Z"
        }
        `
        > Зачем: сервер записывает ACK и отдает UI человеку удобным текстом.

        ## Обработка на сервере
        - mqtt_publisher публикует команды; при ошибке соединения возвращает 502 в REST.
        - MqttStateSubscriber и MqttAckSubscriber подписываются на +/state и +/ack, парсят JSON через Pydantic, обновляют DeviceShadowStore и AckStore.
        - DeviceShadowStore вычисляет 
emaining_s, is_online и offline_reason (см. метод get_manual_watering_view).
        - REST-ручки /api/manual-watering/status, /wait-ack, /ack извлекают данные и управляют таймаутами (15 секунд максимум ожидания ACK).
