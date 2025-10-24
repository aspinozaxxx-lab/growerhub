# Деплой и обновления

## GitHub Actions
- Workflow .github/workflows/ci-cd.yml срабатывает на push в main и вручную через workflow_dispatch.
- Job 	est: checkout, Python 3.11, установка зависимостей (server/requirements.txt), запуск pytest с артефактом отчёта.
- Job deploy (зависит от 	est): при успехе и ветке main настраивает SSH, rsync'ит репозиторий на growerhub.ru, собирает виртуальное окружение и перезапускает growerhub.service.

## Deploy-agent на сервере
- deploy_agent.py раз в минуту проверяет https://api.github.com/repos/aspinozaxxx-lab/growerhub/commits/main.
- При новом SHA агент запускает deploy.sh под пользователем watering-admin.
- deploy.sh обновляет рабочий каталог (git fetch && git reset --hard), активирует virtualenv server/venv, устанавливает зависимости, прогоняет pytest и перезапускает systemd-сервис только при успешных тестах.

## Ansible-плейбуки
- nsible/playbooks/bootstrap.yml готовит сервер: пользователи, firewall, базовый софт.
- nsible/playbooks/nginx.yml, mosquitto.yml, astapi.yml и deploy-agent.yml накатывают конфигурацию ролей.
- nsible/roles/* содержат шаблоны конфигов (например, site.conf.j2 для Nginx, mosquitto.conf для брокера).

## OTA для устройств
- Администратор загружает прошивку через POST /api/upload-firmware (файл попадает в server/firmware_binaries).
- Флаг обновления устанавливается через POST /api/device/{device_id}/trigger-update — устройство увидит новый irmware_url в ответе /api/device/{id}/firmware.
