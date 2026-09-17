---
translation_of: mqtt-discovery-home-assistant
slug: mqtt-discovery-in-home-assistant-how-to-add-plant-sensors-without-manual-yaml
title: 'MQTT discovery in Home Assistant: how to add plant sensors without manual YAML'
summary: >-
  MQTT discovery in Home Assistant: a sensor JSON example, state_topic and
  expire_after checks, troubleshooting and GrowerHub integration limits.
created_at: '2026-07-23'
updated_at: '2026-09-17'
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

MQTT discovery lets a device or service describe its entities to Home Assistant through MQTT. A configuration message creates a sensor or another entity without manual YAML. For example, ESP32 firmware with discovery support can announce air temperature and soil moisture. Supporting MQTT alone does not imply discovery support.

A maintainable setup needs clear topics, a stable identifier and checks for stale readings. The following tutorial sensor is separate from your equipment: it does not control a pump or connect to GrowerHub.

## Example: tutorial greenhouse temperature

Connect Home Assistant's MQTT integration to your broker. Check that discovery is enabled with the `homeassistant` prefix. In its MQTT publishing tool, send this payload to `homeassistant/sensor/tutorial_greenhouse_1_air/config` with retain enabled:

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

Then publish to `tutorial/greenhouse_1/state` without retain:

```json
{"temperature": 24.6}
```

The `Tutorial greenhouse 1` device should contain a sensor reading 24.6 °C. Publish 25.1 instead to check updates. After 180 seconds without a new message, the sensor becomes unavailable. This tests message handling, not a physical measurement. See the [MQTT discovery specification](https://www.home-assistant.io/integrations/mqtt/#mqtt-discovery).

## What should be in the configuration

The entity must have a stable `unique_id`. It should not change when the zone is renamed. We need `state_topic`, from where Home Assistant reads the value. The `device` block is useful for the device: identifiers, name, model, manufacturer or other fields. This is how several sensors of one controller are combined into one device.

For plant sensors, enter `device_class` and `unit_of_measurement` when appropriate: temperature, humidity, percentage, liters. If the value comes in JSON, use template, but keep the format stable.

## Availability and expire_after

Home Assistant can show the unavailable entity if there is no fresh data or a separate availability payload has arrived. This is important for irrigation sensors. The old retained message should not look like actual humidity. The MQTT sensor has `expire_after`, which helps to consider the state as stale after lack of updates.

An availability topic reports connection status; `expire_after` limits the age of a reading. These are separate checks: a controller can remain online while a sensor stops updating. See the [MQTT Sensor documentation](https://www.home-assistant.io/integrations/sensor.mqtt/) for options and limitations.

## Discovery and retained

Configuration discovery messages are often retained so that Home Assistant sees them after a restart. But sensor states with retain require caution: Home Assistant can get the old value instantly. For soil moisture, it is better to rely on fresh update or expire_after rather than consider the retained state to be alive.

If the device is restarted, it must re-publish discovery or respond to the birth message Home Assistant. This reduces the chance that entities will be left without configuration after a restart.

## If the entity does not appear

- Check that Home Assistant and the publisher use the same broker and discovery prefix.
- If the entity exists but has no value, compare `state_topic` and the JSON `temperature` key with the configuration.
- For duplicates, check `unique_id` and old configurations. Renaming a greenhouse should not create a new sensor identifier.
- For devices already connected to Zigbee2MQTT, use its [Home Assistant integration](https://www.zigbee2mqtt.io/guide/usage/integrations/home_assistant.html) instead of manually creating a second copy of the sensor.

## What GrowerHub supports

The current GrowerHub version does not publish HA discovery for its zones or import arbitrary Home Assistant entities. The tutorial MQTT sensor above will not automatically appear in GrowerHub.

The supported route for an existing installation is an MQTT bridge to **Zigbee2MQTT**, forwarding device metadata and readings. Home Assistant and its history stay in place. ESPHome native API, an arbitrary MQTT controller or discovery configuration alone does not establish compatibility. Follow the [GrowerHub, Zigbee2MQTT and Home Assistant connection guide](/articles/growerhub-i-home-assistant-cherez-mqtt/).

## Try the greenhouse overview before setting up a bridge

If your readings are already visible in HA, see whether a ready-made growing interface helps: [open four virtual greenhouses](/app/demo/?lang=en&view=overview), compare their temperatures and click a reading to see its history. Then explore the plants and watering journal.

The demo needs no registration or equipment. Guest changes remain available for 24 hours; saving to an account lets you continue later. It demonstrates the application with virtual devices; your equipment's compatibility requires a separate check. The [short demo walkthrough](/articles/mini-ferma-iz-dvuh-grouboksov-dashboard/) explains the first steps.
