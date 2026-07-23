---
translation_of: growerhub-i-home-assistant-cherez-mqtt
slug: growerhub-and-home-assistant-via-mqtt-practical-integration-scheme
title: 'GrowerHub and Home Assistant via MQTT: practical integration scheme'
summary: >-
  How to connect GrowerHub and Home Assistant via MQTT: zones, sensors,
  commands, discovery, event log and boundaries of responsibility.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: home-assistant-i-diy
tags:
  - GrowerHub
  - Home Assistant
  - MQTT
keywords:
  - GrowerHub Home Assistant
  - MQTT automatic watering
  - Home Assistant plants
related:
  - home-assistant-dlya-rasteniy
  - mqtt-avtopoliv-kakie-topiki-nuzhny
  - mqtt-discovery-home-assistant
  - lokalnaya-avtomatizatsiya-bez-oblaka
hero_image: /content/articles/illustrations/growerhub-i-home-assistant-cherez-mqtt.webp
hero_alt: >-
  Illustration of GrowerHub: MQTT connects GrowerHub, Home Assistant, sensors
  and pump
---
![Illustration GrowerHub: MQTT connects GrowerHub, Home Assistant, sensors and pump](/content/articles/illustrations/growerhub-i-home-assistant-cherez-mqtt.webp)

GrowerHub and Home Assistant complement each other well if responsibility is shared in advance. Home Assistant is convenient as a universal center for smart home and local integrations. GrowerHub stores plant context: zones, watering, diary, tips, reports and restrictions. MQTT can become a connecting layer between them.

Poor integration looks like a chaotic exchange of topics. A good one is like a clear model: what data is transmitted, who makes the decision, where the log is stored and what happens if the connection is lost.

## What to give to Home Assistant

In Home Assistant it is useful to publish current values: soil moisture, temperature, air humidity, leakage, pump status, light, ventilation, service mode. It gives dashboard, automation and general notifications. For entities, you can use MQTT discovery so as not to configure each one manually.

But not every GrowerHub field needs to become a separate entity. It is better to leave diary notes, long reports and advisor explanations in GrowerHub, and transfer key statuses and events to Home Assistant.

## What GrowerHub can read

GrowerHub can read data from Zigbee2MQTT, ESPHome or Home Assistant through MQTT if the topics are stable. For example, Home Assistant already has a leak sensor, and GrowerHub uses it as an emergency stop for watering. It is important to keep a connection with the zone and check the freshness of the data.

If the device is not available, GrowerHub should not silently use the last value. Availability and timestamp are required for automatic watering.

## Who controls the pump

The most important question is where the pump crew lives. If Home Assistant controls the outlet and GrowerHub decides when to water, a clear command and confirmation is needed. If GrowerHub controls the pump directly, Home Assistant can only display status and send manual commands with restrictions.

Do not allow two independent automations that turn on the same pump according to different rules. This leads to conflicts and difficult diagnostics. The structure of topics for commands is described in the article [MQTT-automated watering](/articles/mqtt-avtopoliv-kakie-topiki-nuzhny).

## Event log

Each action must have a source: GrowerHub, Home Assistant, Node-RED, operator, emergency stop. If the command went through MQTT, the log should show not only the status of the outlet, but also the reason. Otherwise, after a week it will be impossible to understand why the pump turned on.

## Conclusion

Integration of GrowerHub and Home Assistant through MQTT should be explicit: zones, topics, availability, commands, confirmations and log. Home Assistant remains a strong smart home hub, GrowerHub is a plant care system. MQTT links them, but does not replace the responsibility architecture.
