---
translation_of: mqtt-avtopoliv-kakie-topiki-nuzhny
slug: mqtt-for-automated-watering-topics-for-sensors-and-commands
title: 'MQTT for automated watering: topics for sensors and commands'
summary: >-
  How to design MQTT topics for automatic watering: sensor states, pump
  commands, availability, events, log and security.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: home-assistant-i-diy
tags:
  - GrowerHub
  - MQTT
  - automatic watering
keywords:
  - MQTT automatic watering
  - Home Assistant automatic watering
  - MQTT watering topics
related:
  - mqtt-discovery-home-assistant
  - growerhub-i-home-assistant-cherez-mqtt
  - node-red-scenarii-poliva
  - bezopasnyy-avtopoliv-limity-i-avariynyy-stop
hero_image: /content/articles/illustrations/mqtt-avtopoliv-kakie-topiki-nuzhny.webp
hero_alt: >-
  Illustration GrowerHub: MQTT-topics of sensors, pump commands and
  auto-irrigation events
---
![Illustration GrowerHub: MQTT-topics of sensors, pump commands and automated watering events](/content/articles/illustrations/mqtt-avtopoliv-kakie-topiki-nuzhny.webp)

MQTT for automatic watering should be clear a month after launch. If topics are named randomly, automation quickly becomes fragile: it is unclear where the sensor status is, where the pump command is, where availability is, and where the event log is. A good topic structure makes the system readable for GrowerHub, Home Assistant, Node-RED and humans.

It is better to design from zones. Not "sensor 1", but a zone, role and parameter. For example: greenhouse on the left, soil moisture, pump condition, leakage, watering command. Then replacing the device does not destroy the meaning of the data.

## States

Sensor states must be published separately from commands. Soil moisture, temperature, air humidity and leakage require clear payloads and units of measurement. If the sensor sends JSON, keep the fields stable: `value`, `unit`, `battery`, `updated_at`, or a similar structure.

For Home Assistant it is important that the entity gets its state from the predictable `state_topic`. If MQTT discovery is used, the configuration must specify the correct topics, unique identifiers and device metadata. Details are in the article [MQTT discovery Home Assistant](/articles/mqtt-discovery-home-assistant).

## Teams

The pump command should not be the same topic as the state. Otherwise it is easy to get a loop or misinterpretation. Separate `command` and `state`. The command can be "start", "stop" or a specified duration, but the controller must still check the limits locally.

For critical devices, it is useful to publish confirmation: command accepted, pump on, pump off, stopped by limit. GrowerHub must see not only the intention to turn on the pump, but also the actual event.

## Availability

A separate layer is accessibility. If the humidity sensor has not been updated in a long time, the automatic watering rule should not consider the old value fresh. Availability can be obtained from Zigbee2MQTT, ESPHome, your own controller, or calculated based on the time of the last message.

The Home Assistant MQTT sensor has a state expiration option that makes the sensor unavailable after no updates. In GrowerHub the same principle is needed for safety: no fresh data - no risky automatic watering.

## Events and log

Events are different from states. Watering was completed, the limit was triggered, the service mode was turned on, a leak blocked the pump, the operator started it manually. These messages are needed for history and reporting. They must not overwrite the current state of the sensor.

The event log is especially important when integrating with Node-RED. The thread can make a decision, but GrowerHub must record why it happened. Node-RED scenarios are discussed in the article [Node-RED irrigation scenarios](/articles/node-red-scenarii-poliva).

## Conclusion

For MQTT automated watering, separate topics are needed for states, commands, availability and events. Structure them by zones, do not mix command and state, check the freshness of the data and record the result of the action. Then MQTT remains an infrastructure and not a source of confusion.
