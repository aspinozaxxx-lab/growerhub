# Инструкции по развёртыванию

## Серверная часть
1. Подготовьте Ubuntu-хост, заведите пользователя watering-admin (см. nsible/playbooks/bootstrap.yml).
2. Запустите Ansible-плейбуки: nsible-playbook -i inventory/production ansible/playbooks/nginx.yml и mosquitto.yml.
3. Настройте DNS и сертификаты Let's Encrypt (плейбук создаёт каталог /var/www/letsencrypt).
4. Подключите GitHub Actions secrets (SSH_PRIVATE_KEY) и убедитесь, что growerhub.service доступен через systemd.

## Устройство ESP32
1. Сборка прошивки: pio run -e esp32dev.
2. Залейте бинарник: pio run -t upload -e esp32dev.
3. Подготовьте конфиг Wi-Fi и адрес сервера (через SPIFFS или actoryReset).
4. Проверка: в Serial-мониторе появится === GrowerHub Ready ===, а сервер получит первый /status.

## OTA-обновление
1. Соберите новый .bin, загрузите через POST /api/upload-firmware (укажите ersion).
2. Выполните POST /api/device/{device_id}/trigger-update.
3. Устройство запросит /api/device/{device_id}/firmware и скачает новый бинарник.

## Диагностика
- journalctl -u growerhub.service -f — следить за бекендом.
- sudo tail -f /var/log/nginx/growerhub.ru_ssl_error.log — ошибки прокси.
- mosquitto_sub -t 'gh/dev/+/state' -v — смотреть состояние полива в реальном времени.
