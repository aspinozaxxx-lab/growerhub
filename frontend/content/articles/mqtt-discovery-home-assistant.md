---
slug: "mqtt-discovery-home-assistant"
title: "MQTT discovery в Home Assistant: как добавить датчики растений без ручного YAML"
summary: "MQTT discovery в Home Assistant: пример JSON для датчика, проверка state_topic и expire_after, поиск ошибок и границы подключения GrowerHub."
created_at: "2026-06-29"
updated_at: "2026-09-17"
cluster: "home-assistant-i-diy"
tags:
  - "GrowerHub"
  - "Home Assistant"
  - "MQTT discovery"
keywords:
  - "MQTT discovery Home Assistant"
  - "MQTT sensor Home Assistant"
  - "Home Assistant растения"
related:
  - "mqtt-avtopoliv-kakie-topiki-nuzhny"
  - "growerhub-i-home-assistant-cherez-mqtt"
  - "esp32-datchik-vlazhnosti-home-assistant"
  - "sovmestimost-zigbee2mqtt-exposes-availability"
hero_image: "/content/articles/illustrations/mqtt-discovery-home-assistant.webp"
hero_alt: "Иллюстрация GrowerHub: MQTT discovery создает датчики растений в Home Assistant"
---

![Иллюстрация GrowerHub: MQTT discovery создает датчики растений в Home Assistant](/content/articles/illustrations/mqtt-discovery-home-assistant.webp)

MQTT discovery позволяет устройству или сервису описать свои сущности для Home Assistant через MQTT. Вместо ручного YAML публикуется конфигурационное сообщение, по которому Home Assistant создаёт датчик или другую сущность. Например, прошивка ESP32 с поддержкой discovery может объявить температуру воздуха и влажность почвы. Само наличие MQTT ещё не означает поддержку discovery.

Для устойчивой настройки нужны понятные топики, стабильный идентификатор и проверка свежести показаний. Ниже — отдельный учебный датчик; он не управляет насосом и не подключается к GrowerHub.

## Пример: температура учебной теплицы

Подключите интеграцию MQTT в Home Assistant к своему брокеру. В её настройках проверьте, что discovery включён и используется префикс `homeassistant`. Откройте публикацию MQTT-пакета и отправьте в топик `homeassistant/sensor/tutorial_greenhouse_1_air/config` с включённым retain:

```json
{
  "name": "Air temperature",
  "unique_id": "tutorial_greenhouse_1_air",
  "state_topic": "tutorial/greenhouse_1/state",
  "value_template": "{{ value_json.temperature }}",
  "device_class": "temperature",
  "unit_of_measurement": "°C",
  "state_class": "measurement",
  "expire_after": 180,
  "device": {
    "identifiers": ["tutorial_greenhouse_1"],
    "name": "Tutorial greenhouse 1"
  }
}
```

Теперь отправьте в `tutorial/greenhouse_1/state` без retain:

```json
{"temperature": 24.6}
```

В устройстве `Tutorial greenhouse 1` должен появиться датчик со значением 24,6 °C. Отправьте `{"temperature": 25.1}`, чтобы проверить обновление. Если новые сообщения не приходят, через 180 секунд датчик станет недоступным. Это проверка обработки сообщений, а не физическое измерение. Формат описан в [документации MQTT discovery](https://www.home-assistant.io/integrations/mqtt/#mqtt-discovery).

## Что должно быть в конфигурации

У сущности должен быть стабильный `unique_id`. Он не должен меняться при переименовании зоны. Нужен `state_topic`, откуда Home Assistant читает значение. Для устройства полезен блок `device`: identifiers, name, model, manufacturer или другие поля. Так несколько сенсоров одного контроллера объединяются в одно устройство.

Для датчиков растений указывайте `device_class` и `unit_of_measurement`, когда они подходят: температура, влажность, проценты, литры. Если значение приходит в JSON, используйте template, но держите формат стабильным.

## Availability и expire_after

Home Assistant может показывать сущность unavailable, если нет свежих данных или пришел отдельный availability payload. Для датчиков полива это важно. Старое retained-сообщение не должно выглядеть как актуальная влажность. В MQTT sensor есть `expire_after`, который помогает считать состояние устаревшим после отсутствия обновлений.

Топик доступности показывает состояние подключения, а `expire_after` ограничивает возраст показания. Это разные проверки: контроллер может быть онлайн, но перестать обновлять датчик. Параметры и ограничения перечислены в [документации MQTT Sensor](https://www.home-assistant.io/integrations/sensor.mqtt/).

## Discovery и retained

Конфигурационные discovery-сообщения часто сохраняют retained, чтобы Home Assistant увидел их после перезапуска. Но состояния датчиков с retain требуют осторожности: Home Assistant может получить старое значение мгновенно. Для влажности почвы лучше полагаться на fresh update или expire_after, а не считать retained state живым.

Если устройство перезапускается, оно должно заново публиковать discovery или реагировать на birth-сообщение Home Assistant. Это снижает шанс, что сущности останутся без конфигурации после рестарта.

## Если сущность не появилась

- Проверьте, что Home Assistant и отправитель подключены к одному брокеру, а discovery-префикс совпадает.
- Если сущность есть, но значение пустое, сравните `state_topic` и ключ `temperature` в JSON с конфигурацией.
- При дублях проверьте `unique_id` и старые конфигурации. Переименование теплицы не должно создавать новый идентификатор датчика.
- Для уже подключённых устройств Zigbee2MQTT используйте его [штатную интеграцию с Home Assistant](https://www.zigbee2mqtt.io/guide/usage/integrations/home_assistant.html), вместо ручного создания второй копии датчика.

## Что поддерживает GrowerHub

В текущей версии GrowerHub не публикует HA discovery для своих зон и не импортирует произвольные сущности Home Assistant. Описанный выше учебный MQTT-датчик автоматически в GrowerHub не появится.

Поддержанный путь для существующей установки — MQTT-мост к **Zigbee2MQTT**: он передаёт сведения об устройствах и их показания. Home Assistant и его история остаются на месте. ESPHome native API, произвольный MQTT-контроллер и discovery-конфигурация сами по себе не обеспечивают совместимость. Шаги и ограничения есть в [инструкции подключения GrowerHub к Zigbee2MQTT и Home Assistant](/articles/growerhub-i-home-assistant-cherez-mqtt/).

## Попробуйте обзор теплиц до настройки моста

Если показания уже видны в HA, оцените, полезен ли вам готовый интерфейс выращивания: [откройте четыре виртуальные теплицы](/app/demo/?lang=ru&view=overview), сравните температуру и нажмите на показание, чтобы увидеть историю. Затем откройте растения и журнал поливов.

Регистрация и оборудование для демо не нужны. Гостевые изменения доступны 24 часа; сохранение в аккаунт позволяет продолжить позже. Демо показывает работу приложения с виртуальными устройствами, а совместимость вашего оборудования проверяется отдельно. [Короткое руководство по демоферме](/articles/mini-ferma-iz-dvuh-grouboksov-dashboard/) поможет пройти первые действия.
