---
translation_of: mqtt-discovery-home-assistant
slug: mqtt-discovery-in-home-assistant-how-to-add-plant-sensors-without-manual-yaml
title: 'MQTT discovery in Home Assistant: how to add plant sensors without manual YAML'
summary: >-
  How MQTT discovery works in Home Assistant, what fields are needed for plant
  sensors and why unique_id, device and availability are important.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: home-assistant-i-diy
tags:
  - GrowerHub
  - Home Assistant
  - MQTT discovery
keywords:
  - MQTT discovery Home Assistant
  - MQTT sensor Home Assistant
  - Home Assistant plants
related:
  - mqtt-avtopoliv-kakie-topiki-nuzhny
  - growerhub-i-home-assistant-cherez-mqtt
  - esp32-datchik-vlazhnosti-home-assistant
  - sovmestimost-zigbee2mqtt-exposes-availability
hero_image: /content/articles/illustrations/mqtt-discovery-home-assistant.webp
hero_alt: 'Illustration GrowerHub: MQTT discovery creates plant sensors in Home Assistant'
---
![Illustration GrowerHub: MQTT discovery creates plant sensors in Home Assistant](/content/articles/illustrations/mqtt-discovery-home-assistant.webp)

MQTT discovery allows a device or service to describe its entities to Home Assistant via MQTT. Instead of manual YAML, a configuration message is published and Home Assistant creates a sensor, binary_sensor, switch, or other entity. It is convenient for plants: GrowerHub, ESP32 or other controller can announce soil moisture, temperature, leakage and pump status.

But discovery does not replace careful design. If you do not set `unique_id`, device metadata, state topic, and availability, entities will be confused, duplicated, or remain “available” under an outdated retained message. For automatic watering, this is not a trifle, but the risk of making the wrong decision.

## What should be in the configuration

The entity must have a stable `unique_id`. It should not change when the zone is renamed. We need `state_topic`, from where Home Assistant reads the value. The `device` block is useful for the device: identifiers, name, model, manufacturer or other fields. This is how several sensors of one controller are combined into one device.

For plant sensors, enter `device_class` and `unit_of_measurement` when appropriate: temperature, humidity, percentage, liters. If the value comes in JSON, use template, but keep the format stable.

## Availability and expire_after

Home Assistant can show the unavailable entity if there is no fresh data or a separate availability payload has arrived. This is important for irrigation sensors. The old retained message should not look like actual humidity. The MQTT sensor has `expire_after`, which helps to consider the state as stale after lack of updates.

In GrowerHub the same principle should be used in the rules: if the sensor is not available, automatic watering should not start at the old value. Availability is not a decorative status, but a part of security.

## Discovery and retained

Configuration discovery messages are often retained so that Home Assistant sees them after a restart. But sensor states with retain require caution: Home Assistant can get the old value instantly. For soil moisture, it is better to rely on fresh update or expire_after rather than consider the retained state to be alive.

If the device is restarted, it must re-publish discovery or respond to the birth message Home Assistant. This reduces the chance that entities will be left without configuration after a restart.

## GrowerHub and Home Assistant

GrowerHub can publish discovery for its zones or read existing entities through MQTT. In both cases, the correspondence map is important: zone GrowerHub, MQTT topic, Home Assistant entity, physical device. Without this map, integration becomes a collection of similar names.

A broader scenario is described in the article [GrowerHub and Home Assistant via MQTT](/articles/growerhub-i-home-assistant-cherez-mqtt).

## Conclusion

MQTT discovery simplifies adding plant sensors to Home Assistant, but requires stable identifiers, correct topics and availability. For GrowerHub, this is a good way of integration, if we do not forget that automatic watering should make decisions only based on fresh and understandable data.
