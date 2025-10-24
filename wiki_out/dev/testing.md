# Тестирование

        ## Как запускать локально
        `ash
        cd server
        python -m venv .venv
        . .venv/bin/activate
        pip install -r requirements.txt
        pytest -q
        `
        Pytest использует in-memory SQLite (psycopg2 не требуется) и проверяет MQTT-слой через mock-и.

        ## Что покрыто
        - 	est_api_manual_watering_start/stop/wait_ack — happy-путь и валидация конфликтов (повторный старт, остановка без запуска), обработка 502/408.
        - 	est_mqtt_protocol.py — сериализация/десериализация команд, ack и state, генерация топиков.
        - 	est_mqtt_ack_and_api.py, 	est_mqtt_subscriber_handler.py — интеграция подписчиков с AckStore и DeviceShadowStore.
        - 	est_device_shadow_and_status_api.py — edge-кейсы расчёта 
emaining_s, флагов офлайна и ответов API.

        ## В CI/CD
        - GitHub Actions запускает pytest -q --maxfail=1 --disable-warnings --junitxml=pytest-report.xml.
        - Отчёт прикладывается как артефакт pytest-report для анализа падений.
        - Deploy-agent на сервере повторно прогоняет pytest и отменяет рестарт сервиса при провале.
