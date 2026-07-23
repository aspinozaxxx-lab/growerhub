---
translation_of: podklyuchit-zigbee-datchik-temperatury-vlazhnosti
slug: how-to-connect-a-zigbee-temperature-and-humidity-sensor-step-by-step-check
title: 'How to connect a Zigbee temperature and humidity sensor: step-by-step check'
summary: >-
  Connecting a Zigbee temperature and humidity sensor to Zigbee2MQTT: model
  selection, pairing, exposes, availability, installation location and data
  verification.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - Zigbee
  - sensor
keywords:
  - connect Zigbee sensor
  - Zigbee temperature humidity sensor
  - Zigbee2MQTT sensor
related:
  - pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya
  - zigbee-dlya-teplitsy-kakie-ustroystva-polezny
  - sovmestimost-zigbee2mqtt-exposes-availability
  - kontrol-mikroklimata-v-teplitse
hero_image: >-
  /content/articles/illustrations/podklyuchit-zigbee-datchik-temperatury-vlazhnosti.webp
hero_alt: Connecting the Zigbee temperature and humidity sensor to the Zigbee2MQTT
---
![Connecting the Zigbee temperature and humidity sensor to Zigbee2MQTT](/content/articles/illustrations/podklyuchit-zigbee-datchik-temperatury-vlazhnosti.webp)

Connecting a Zigbee sensor means not only seeing its name in the interface. The work area requires reliable temperature and humidity, a clear update frequency, accessibility control, and the correct installation location. Below is the sequence from choosing a model to binding to GrowerHub.

## What to check before purchasing

| Parameter | Why is it needed |
|---|---|
| exact model and manufacturer | the same body can hide different revisions |
| `temperature` and `humidity` in exposes | confirms what data a particular model provides |
| power and battery type | affects maintenance and operation in the cold |
| reporting frequency | too sparse data is inconvenient for quick ventilation control |
| operating conditions | household case does not become waterproof due to Zigbee |

Find the model in the [official catalog Zigbee2MQTT](https://www.zigbee2mqtt.io/supported-devices/) and open its page. Look at the available properties and pairing instructions, and not just the “supported” mark.

## Step 1: Prepare the network

Make sure the coordinator is working and other devices are transmitting data. For the first connection, place the sensor next to the coordinator. If the work area is far away, add a network-powered Zigbee router in advance; battery sensors usually do not relay messages.

It is better to remove the USB coordinator from the computer, SSD and Wi-Fi equipment with an extension cable. Detailed recommendations are collected in the Zigbee2MQTT documentation on [network range and stability](https://www.zigbee2mqtt.io/advanced/zigbee/02_improve_network_range_and_stability.html).

## Step 2. Perform pairing

1. Install a fresh battery;
2. enable permit join for a limited time;
3. Reset according to the instructions of the exact model;
4. keep the sensor nearby until the interview is completed;
5. For a sleeping device, if necessary, briefly press the button to prevent it from falling asleep during polling;
6. Close permit join after connecting.

Official connection options via the interface and MQTT are described in [Allowing devices to join](https://www.zigbee2mqtt.io/guide/usage/pairing_devices.html). If the process does not complete, use a separate [checklist for a missing or non-connecting Zigbee device](/articles/pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya/).

## Step 3: Check fields and units

After the interview, wait for several messages and compare the readings with a working control device. Check:

- `temperature` in degrees Celsius;
- `humidity` in percent relative humidity;
- `battery` or `voltage`, if the model transmits them;
- time of last message;
- availability and diagnostic `linkquality`.

Do not build automation based on the first package. The sensor needs to equalize the temperature after the street or warm hands. A small constant error can be taken into account by the correction, but jumps and stuck values first require diagnostics.

## Step 4: Give it a meaningful name

The name must survive the reshuffling of equipment. `sensor_01` and “sensor on the left” quickly become meaningless. A practical option is the zone and role: “Seedling · air” or `seedlings_air`. The hardware address is stored separately and is not needed on public screens.

In GrowerHub, the sensor is tied to a specific room or box. This way the value appears next to the equipment in that zone and is not mixed with the average for the entire farm.

## Step 5. Select installation location

For plants, the sensor is usually placed at leaf level, in the shade, away from the direct flow of a fan, lamp, humidifier or wet tray. In a long greenhouse, one sensor does not describe both ends. First compare the zones, then decide whether they can be combined.

Leave the sensor for 24-48 hours and match the schedule with turning on the lights, ventilation and watering. A sharp step without a physical event, a long straight line or regular skips is a reason to check the network and device.

## Step 6. Set up accessibility

Zigbee2MQTT distinguishes between active and passive devices: a sleeping battery sensor cannot be interrogated like a socket. The official behavior and timeouts are described in [Device Availability](https://www.zigbee2mqtt.io/guide/configuration/device-availability.html).

For control, also set the permissible measurement age. For example, a ventilation scenario may require more recent data than a weekly report. If the value is deprecated, it is safer to show a warning and not trigger a new action.

## Restrictions

A household Zigbee sensor is not an industrial measuring device and often does not have condensation protection. Do not place it where the body gets exposed to water. For critical ventilation, use independent limits and check the actual result, not just the relay command.

An example of how multiple zones and data freshness are collected in GrowerHub is shown on the [mini-farm automation](/avtomatizatsiya-mini-fermy/#demo-ekrany) page.

## Done if

- exact model and exposes verified;
- pairing completed without errors interview;
- temperature and humidity are plausible;
- the name indicates the zone and role;
- after the transfer the connection is stable;
- availability and age of data are taken into account;
- the sensor is not installed near a source of heat, water or direct air flow.

Only after this check should the data be used in the GrowerHub, Home Assistant or other automation rules.

If the sensor has not yet been selected, look at [multiple compatible examples](/oborudovanie/datchiki/) and be sure to check the exact model ID against Zigbee2MQTT. The GrowerHub connection itself begins without a questionnaire about plants: [seven steps from the entrance to the zone](/kak-nachat/).
