---
translation_of: zigbee2mqtt-prostymi-slovami
slug: zigbee2mqtt-in-simple-words-how-the-sensor-coordinator-and-mqtt-are-connected
title: >-
  Zigbee2MQTT in simple words: how the sensor, coordinator and MQTT are
  connected
summary: >-
  A clear diagram of Zigbee2MQTT: what the coordinator, routers, MQTT broker,
  topics, exposes and availability do and how GrowerHub applies it.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - Zigbee2MQTT
  - MQTT
keywords:
  - Zigbee2MQTT in simple words
  - what is Zigbee2MQTT
  - Zigbee Hub
related:
  - roli-zigbee-ustroystv-v-growerhub
  - sovmestimost-zigbee2mqtt-exposes-availability
  - growerhub-i-home-assistant-cherez-mqtt
  - mqtt-avtopoliv-kakie-topiki-nuzhny
hero_image: /content/articles/illustrations/zigbee2mqtt-prostymi-slovami.webp
hero_alt: 'Schema Zigbee2MQTT: device, coordinator, MQTT broker and GrowerHub'
---
![Scheme Zigbee2MQTT: device, coordinator, MQTT broker and GrowerHub](/content/articles/illustrations/zigbee2mqtt-prostymi-slovami.webp)

Zigbee2MQTT is an intermediary program between the Zigbee network and MQTT. The sensor talks via Zigbee to the coordinator, Zigbee2MQTT translates its message into understandable data, and the MQTT broker delivers it to GrowerHub, Home Assistant or another service. In the opposite direction, the relay command follows the same path.

## System by layers

| Layer | What does | Example |
|---|---|---|
| Zigbee-device | measures or executes a command | temperature sensor, socket, relay |
| coordinator | creates one Zigbee-network | USB adapter with supported firmware |
| Zigbee2MQTT | polls devices and converts messages | temperature turns into JSON-field |
| MQTT-broker | delivers messages to subscribers | Mosquito |
| application | shows data and applies rules | GrowerHub or Home Assistant |

If the sensor is missing, the fault can be on any layer. Therefore, the phrase “Zigbee2MQTT does not work” is too general: first you need to understand whether the adapter sees the network, whether pairing is completed, whether the MQTT message is published and whether the consumer has read it.

## Coordinator, router and end device

There is only one coordinator in the network. Network-powered devices often act as routers and forward messages. Battery sensors usually sleep and are end devices. They save power, but do not strengthen the network.

For a greenhouse, this means that the long-range battery sensor cannot be “amplified” by a second battery sensor. You need a suitable router between it and the coordinator. Metal, wet structures, and 2.4 GHz equipment further impact communications. Practical recommendations can be found in the official instructions [Improve network range and stability](https://www.zigbee2mqtt.io/advanced/zigbee/02_improve_network_range_and_stability.html).

## What happens when you connect

1. Zigbee2MQTT temporarily allows new devices to enter the network.
2. The device is put into pairing or a factory reset is performed.
3. It joins the network and passes the interview.
4. Zigbee2MQTT defines the model and its capabilities.
5. States begin to be published in MQTT.

The official process and connection through a specific router is described in [Allowing devices to join](https://www.zigbee2mqtt.io/guide/usage/pairing_devices.html). If the interview does not end, proceed to the step-by-step [diagnostics of the Zigbee device](/articles/pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya/).

## Topics without unnecessary theory

MQTT-topic is similar to the address of the message. The device publishes its state to a topic with a friendly name, and the command is usually sent to the associated topic `/set`. Service operations go through `bridge/request` and `bridge/response`.

The exact names depend on the configured base topic. Don't tie business logic to a random name like `0xa4...`: give the device a stable friendly name, and store the hardware address as a technical attribute.

## What is exposes

`Exposes` - a list of properties and actions that Zigbee2MQTT knows for a specific model: temperature, humidity, charge, relay state, brightness and other capabilities. The presence of the Zigbee logo does not guarantee the required field. Please check the [Supported Devices Catalog](https://www.zigbee2mqtt.io/supported-devices/) for the exact model before purchasing.

For GrowerHub, the direction of the property is also important: one value can only be read, the other can be manipulated. A pump or light command should only be sent for an explicitly supported managed property.

## Availability and data freshness

Availability indicates whether Zigbee2MQTT considers the device accessible. Active and sleeping devices are checked with different timeouts. Details and the MQTT topic are described in [Device Availability](https://www.zigbee2mqtt.io/guide/configuration/device-availability.html).

However, `online` does not mean that a particular dimension is fresh. For control, store the time of the last value. If soil moisture has not been updated for longer than the permissible interval, new watering should be blocked or switched to manual checking.

## How GrowerHub uses this layer

GrowerHub receives states and commands through MQTT, associates the device with the zone and displays it next to the box, plants and scenario. The user does not need to work with technical topics every day, but during diagnostics the message path remains transparent.

An example of such a view is on the page [mini-farm automation](/avtomatizatsiya-mini-fermy/#demo-ekrany): the zone, freshness of readings and control conditions are separately visible.

## Restrictions

- Zigbee2MQTT does not make the household sensor waterproof.
- The supported model may have a different hardware revision.
- A good `linkquality` at one moment does not guarantee stability over the course of a day.
- MQTT delivers the command, but does not confirm the physical result of the pump or fan without feedback.
- Hazardous loads require independent limitations and safe installation.

## What to check after installation

- the coordinator is located away from sources of interference;
- the network has powered routers;
- pairing is completed and the model is recognized;
- the necessary exposes are actually updated;
- availability is included deliberately;
- friendly names are clear;
- GrowerHub blocks action based on outdated data.

So Zigbee2MQTT becomes not “another hub”, but a clear transport layer between devices and the control system.

GrowerHub provides an isolated MQTT space and a ready-made configuration after login. Select [coordinator](/oborudovanie/zigbee-koordinator/), then go through [short connection](/kak-nachat/); an existing local MQTT can be saved via a local bridge.
