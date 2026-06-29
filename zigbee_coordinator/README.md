# Zigbee Router Bridge

Локальный Zigbee-шлюз GrowerHub: USB-донгл CC2652P + Zigbee2MQTT + MQTT broker.

## Структура

- `mosquitto/` - конфиг, база и логи локального MQTT broker, если нужен локальный режим.
- `data/` - рабочие данные Zigbee2MQTT: `configuration.yaml`, локальный `secret.yaml`, база Zigbee-сети, backup coordinator, state, логи.
- `zigbee2mqtt/` - само приложение Zigbee2MQTT `2.12.0` из `https://github.com/Koenkk/zigbee2mqtt`.
- `start-coordinator.bat` - запуск Zigbee2MQTT coordinator.
- `status-coordinator.bat` - проверка состояния Zigbee2MQTT coordinator.
- `stop-coordinator.bat` - остановка Zigbee2MQTT coordinator.

## Как работает

Zigbee2MQTT запускается из папки `zigbee2mqtt/`, использует данные из `data/`, подключается к USB-донглу на `COM7` как `zstack` coordinator и отправляет MQTT-сообщения на broker из `data\configuration.yaml`.

Текущий MQTT broker:

```text
mqtt://growerhub.ru:1883
```

Текущий MQTT namespace:

```text
zigbee2growerhub
```

Веб-интерфейс:

```text
http://127.0.0.1:8080
```

## Запуск

1. Проверить локальные секреты:

```text
data\secret.yaml
```

Если файла нет, скопировать `data\secret.example.yaml` в `data\secret.yaml` и заполнить реальные значения.

2. Запустить coordinator:

```bat
start-coordinator.bat
```

При первом запуске bat сам установит зависимости Zigbee2MQTT через Corepack/pnpm.

После запуска `start-coordinator.bat` можно закрыть консольное окно: Zigbee2MQTT продолжит работать отдельным процессом.

Остановить coordinator:

```bat
stop-coordinator.bat
```

Проверить статус coordinator:

```bat
status-coordinator.bat
```

Повторный запуск `start-coordinator.bat` сначала остановит уже работающий Zigbee2MQTT, затем поднимет новый экземпляр.

## Настройка

Основной конфиг:

```text
data\configuration.yaml
```

MQTT server:

```yaml
mqtt:
  base_topic: zigbee2growerhub
  server: mqtt://growerhub.ru:1883
  user: '!secret mqtt_user'
  password: '!secret mqtt_password'
```

Для локального broker:

```yaml
mqtt:
  base_topic: zigbee2growerhub
  server: mqtt://127.0.0.1:1883
```

## Добавление устройств

Открыть веб-интерфейс, включить `Permit join`, затем перевести устройство в режим сопряжения.
