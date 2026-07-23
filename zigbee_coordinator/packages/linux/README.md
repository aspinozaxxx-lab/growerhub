# GrowerHub coordinator для Raspberry Pi/Linux

1. Установите Docker Engine с Compose plugin.
2. Скопируйте `.env.example` в `.env` и укажите реальный путь устройства, лучше стабильный `/dev/serial/by-id/...`.
3. Создайте каталог `data` и поместите в него отдельно скачанные из кабинета `configuration.yaml` и `secret.yaml`.
4. В `configuration.yaml` замените `CHANGE_ME_SERIAL_PORT` на `/dev/ttyUSB0`, а `CHANGE_ME_ADAPTER` — на `zstack` для ZBDongle-P либо `ember` для ZBDongle-E.
5. Запустите `docker compose up -d` и проверьте `docker compose logs --tail=100`.

Каталог `data` является постоянным volume и сохраняет Zigbee-сеть после перезапуска. В образ и Compose-файл персональные MQTT credentials не встроены.
