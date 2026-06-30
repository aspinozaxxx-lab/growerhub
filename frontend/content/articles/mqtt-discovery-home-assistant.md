---
slug: "mqtt-discovery-home-assistant"
title: "MQTT discovery в Home Assistant: как добавить датчики растений без ручного YAML"
summary: "Как работает MQTT discovery в Home Assistant, какие поля нужны для датчиков растений и почему unique_id, device и availability важны."
created_at: "2026-06-29"
updated_at: "2026-06-30"
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

MQTT discovery позволяет устройству или сервису самому описать свои сущности для Home Assistant через MQTT. Вместо ручного YAML публикуется конфигурационное сообщение, и Home Assistant создает sensor, binary_sensor, switch или другую сущность. Для растений это удобно: GrowerHub, ESP32 или другой контроллер может объявить влажность почвы, температуру, протечку и состояние насоса.

Но discovery не отменяет аккуратного проектирования. Если не задать `unique_id`, device metadata, state topic и availability, сущности будут путаться, дублироваться или оставаться "доступными" по устаревшему retained-сообщению. Для автополива это не мелочь, а риск неверного решения.

## Что должно быть в конфигурации

У сущности должен быть стабильный `unique_id`. Он не должен меняться при переименовании зоны. Нужен `state_topic`, откуда Home Assistant читает значение. Для устройства полезен блок `device`: identifiers, name, model, manufacturer или другие поля. Так несколько сенсоров одного контроллера объединяются в одно устройство.

Для датчиков растений указывайте `device_class` и `unit_of_measurement`, когда они подходят: температура, влажность, проценты, литры. Если значение приходит в JSON, используйте template, но держите формат стабильным.

## Availability и expire_after

Home Assistant может показывать сущность unavailable, если нет свежих данных или пришел отдельный availability payload. Для датчиков полива это важно. Старое retained-сообщение не должно выглядеть как актуальная влажность. В MQTT sensor есть `expire_after`, который помогает считать состояние устаревшим после отсутствия обновлений.

В GrowerHub этот же принцип должен использоваться в правилах: если датчик недоступен, автоматический полив не должен запускаться по старому значению. Availability - не декоративный статус, а часть безопасности.

## Discovery и retained

Конфигурационные discovery-сообщения часто сохраняют retained, чтобы Home Assistant увидел их после перезапуска. Но состояния датчиков с retain требуют осторожности: Home Assistant может получить старое значение мгновенно. Для влажности почвы лучше полагаться на fresh update или expire_after, а не считать retained state живым.

Если устройство перезапускается, оно должно заново публиковать discovery или реагировать на birth-сообщение Home Assistant. Это снижает шанс, что сущности останутся без конфигурации после рестарта.

## GrowerHub и Home Assistant

GrowerHub может публиковать discovery для своих зон или читать уже существующие сущности через MQTT. В обоих случаях важна карта соответствий: зона GrowerHub, MQTT-топик, Home Assistant entity, физическое устройство. Без этой карты интеграция становится набором похожих названий.

Более широкий сценарий описан в статье [GrowerHub и Home Assistant через MQTT](/articles/growerhub-i-home-assistant-cherez-mqtt).

## Вывод

MQTT discovery упрощает добавление датчиков растений в Home Assistant, но требует стабильных идентификаторов, корректных топиков и availability. Для GrowerHub это хороший способ интеграции, если не забывать, что автополив должен принимать решения только по свежим и понятным данным.
