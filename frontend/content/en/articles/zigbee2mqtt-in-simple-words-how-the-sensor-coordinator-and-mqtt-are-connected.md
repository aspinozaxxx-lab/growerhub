---
translation_of: "zigbee2mqtt-prostymi-slovami"
slug: "zigbee2mqtt-in-simple-words-how-the-sensor-coordinator-and-mqtt-are-connected"
title: "What is Zigbee2MQTT? Sensors, coordinator, and MQTT explained"
summary: "A plain-language Zigbee2MQTT diagram: what the coordinator, routers, MQTT broker, topics, exposes, and availability do, and how GrowerHub uses them."
created_at: "2026-07-23"
updated_at: "2026-08-16"
cluster: "zigbee-hub-i-ustroystva"
tags:
  - "GrowerHub"
  - "Zigbee2MQTT"
  - "MQTT"
keywords:
  - "what is Zigbee2MQTT"
  - "Zigbee2MQTT explained"
  - "Zigbee coordinator MQTT"
related:
  - "ustanovka-zigbee2mqtt-na-windows"
  - "roli-zigbee-ustroystv-v-growerhub"
  - "sovmestimost-zigbee2mqtt-exposes-availability"
  - "growerhub-i-home-assistant-cherez-mqtt"
hero_image: "/content/articles/illustrations/zigbee2mqtt-prostymi-slovami.webp"
hero_alt: "Zigbee2MQTT data path from a device through a coordinator and MQTT to GrowerHub"
---

![Zigbee2MQTT data path from a device through a coordinator and MQTT to GrowerHub](/content/articles/illustrations/zigbee2mqtt-prostymi-slovami.webp)

Zigbee2MQTT is software that connects a Zigbee network to MQTT. A sensor talks over Zigbee to the coordinator, Zigbee2MQTT converts its message into structured data, and an MQTT broker delivers that data to GrowerHub, Home Assistant, or another application. A relay command follows the same path in reverse.

## The system in five layers

| Layer | What it does | Example |
|---|---|---|
| Zigbee device | measures a value or performs a command | temperature sensor, smart plug, relay |
| coordinator | creates and manages one Zigbee network | USB adapter with supported coordinator firmware |
| Zigbee2MQTT | translates Zigbee messages and device commands | a temperature report becomes a JSON property |
| MQTT broker | routes messages between clients | Mosquitto or the GrowerHub broker |
| application | presents data and applies rules | GrowerHub or Home Assistant |

When a sensor disappears, the fault can be at any layer. “Zigbee2MQTT is broken” is too broad: determine whether the adapter opened the network, pairing completed, an MQTT message was published, and the application received it.

## What Zigbee2MQTT does not replace

- The **coordinator** is the physical radio adapter; Zigbee2MQTT is the software using it.
- The **MQTT broker** receives and routes messages; Zigbee2MQTT connects to it as a client.
- **Home Assistant or GrowerHub** provides dashboards and user-facing automation.
- The local **Zigbee network does not require internet**. Internet is required when the broker or dashboard is remote, as with a direct GrowerHub connection.

A typical installation therefore has a USB coordinator, Zigbee2MQTT, an MQTT broker, and an application. GrowerHub already provides the broker and dashboard, leaving the user to set up the coordinator and Zigbee2MQTT.

## Coordinator, router, and end device

One coordinator creates the network. Many mains-powered Zigbee devices also act as routers and forward messages. Battery sensors usually sleep and operate as end devices; they conserve energy but do not extend the mesh.

In a greenhouse, a distant battery sensor cannot be “boosted” by placing another battery sensor nearby. Add a suitable powered router between it and the coordinator. Metal, wet structures, and other 2.4 GHz equipment also affect the link. See the official [range and stability guide](https://www.zigbee2mqtt.io/advanced/zigbee/02_improve_network_range_and_stability.html).

## What happens during pairing

1. Zigbee2MQTT temporarily allows new devices to join.
2. The device enters pairing mode or is factory-reset.
3. It joins the network and completes an interview.
4. Zigbee2MQTT identifies the model and converter capabilities.
5. Device states begin to appear in MQTT.

The official sequence and joining through a specific router are documented under [Allowing devices to join](https://www.zigbee2mqtt.io/guide/usage/pairing_devices.html). If interview does not complete, use the [step-by-step pairing diagnostics](/articles/pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya/).

## MQTT topics without unnecessary theory

An MQTT topic is an address for a message. A device normally publishes state under a topic containing its friendly name, and a command goes to the corresponding `/set` topic. Service requests and responses use `bridge/request` and `bridge/response`.

Exact names depend on the configured base topic. Do not tie rules to a temporary hardware-looking name such as `0xa4...`. Give devices stable friendly names and keep the IEEE address as a technical identifier.

## What `exposes` means

`Exposes` is the list of properties and actions that Zigbee2MQTT knows for an exact model: temperature, humidity, battery, relay state, brightness, and other capabilities. A Zigbee logo does not guarantee a particular field. Check the exact model in the [supported-device catalog](https://www.zigbee2mqtt.io/supported-devices/) before buying.

Direction matters as well. Some properties are read-only; others accept a command. GrowerHub sends a light, valve, or pump command only when Zigbee2MQTT marks that capability writable.

## Availability and data freshness

Availability indicates whether Zigbee2MQTT considers a device reachable. Powered devices and sleeping battery sensors use different timeouts; the behavior is documented under [Device Availability](https://www.zigbee2mqtt.io/guide/configuration/device-availability.html).

An `online` device can still have a stale individual measurement. A control rule should track the time of the last relevant value. If soil moisture is older than the accepted interval, a new watering action should be blocked or require manual verification.

## How GrowerHub uses Zigbee2MQTT

GrowerHub receives states and commands through MQTT, associates devices with zones, and presents them beside the relevant equipment and scenarios. Users do not need to work with technical topics every day, but the message path remains clear during diagnosis.

The [farm automation page](/avtomatizatsiya-mini-fermy/#demo-ekrany) shows zones, reading freshness, and control conditions as separate pieces of information.

## Important limitations

- Zigbee2MQTT does not make a consumer sensor waterproof.
- A supported product name may hide another hardware revision.
- One good `linkquality` value does not prove day-long stability.
- MQTT confirms message delivery, not the physical result of a pump or fan without feedback.
- Water and hazardous electrical loads require independent limits and safe installation.

## Installation checklist

- place the coordinator away from strong interference;
- add powered routers where the mesh needs them;
- complete pairing and verify the exact model;
- confirm that required `exposes` really update;
- configure availability deliberately;
- use meaningful friendly names;
- block control when relevant data is stale.

With those boundaries, Zigbee2MQTT is not “another proprietary hub.” It is a transparent transport layer between devices and a control system.

GrowerHub provides an isolated MQTT namespace and ready-to-download configuration after sign-in. Choose a [coordinator](/oborudovanie/zigbee-koordinator/), follow the [short setup path](/kak-nachat/), or keep an existing local broker through the directed connector. For a first PC setup, see [installing Zigbee2MQTT on Windows](/articles/ustanovka-zigbee2mqtt-na-windows/).
