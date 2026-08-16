---
translation_of: "podklyuchit-zigbee-datchik-temperatury-vlazhnosti"
slug: "how-to-connect-a-zigbee-temperature-and-humidity-sensor-step-by-step-check"
title: "Pair a Zigbee temperature and humidity sensor with Zigbee2MQTT"
summary: "A step-by-step Zigbee sensor setup: exact model, pairing and interview, temperature and humidity exposes, availability, placement, and data verification."
created_at: "2026-07-23"
updated_at: "2026-08-16"
cluster: "zigbee-hub-i-ustroystva"
tags:
  - "GrowerHub"
  - "Zigbee"
  - "sensor"
keywords:
  - "pair Zigbee temperature humidity sensor"
  - "Zigbee2MQTT temperature humidity sensor"
  - "connect Zigbee sensor"
related:
  - "ustanovka-zigbee2mqtt-na-windows"
  - "pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya"
  - "zigbee-dlya-teplitsy-kakie-ustroystva-polezny"
  - "sovmestimost-zigbee2mqtt-exposes-availability"
hero_image: "/content/articles/illustrations/podklyuchit-zigbee-datchik-temperatury-vlazhnosti.webp"
hero_alt: "Pairing a Zigbee temperature and humidity sensor with Zigbee2MQTT"
---

![Pairing a Zigbee temperature and humidity sensor with Zigbee2MQTT](/content/articles/illustrations/podklyuchit-zigbee-datchik-temperatury-vlazhnosti.webp)

A sensor is not fully connected just because its name appears in a dashboard. A working zone needs plausible temperature and humidity, a known reporting pattern, availability, and sensible placement. This sequence starts with the exact model and ends with verified data in GrowerHub.

## What to check before buying

| Detail | Why it matters |
|---|---|
| exact model and manufacturer | identical enclosures can contain different hardware revisions |
| `temperature` and `humidity` in `exposes` | confirms the values provided by that converter |
| battery type and power requirements | affects maintenance and cold-weather behavior |
| normal reporting pattern | a slow battery sensor is unsuitable for fast ventilation control |
| environmental rating | Zigbee does not make a consumer enclosure waterproof |

Find the exact model in the [official Zigbee2MQTT device catalog](https://www.zigbee2mqtt.io/supported-devices/) and open its page. Read the capabilities and pairing instructions rather than relying on the “supported” label alone.

## Step 1: Prepare the network

Confirm that Zigbee2MQTT is running and other devices still report. Pair the sensor close to the coordinator first. If its final location is distant, add a suitable mains-powered Zigbee router; sleeping battery sensors normally do not forward messages.

Move a USB coordinator away from the computer chassis, SSDs, and Wi-Fi equipment with an extension cable. Zigbee2MQTT documents the main sources of interference in its [range and stability guide](https://www.zigbee2mqtt.io/advanced/zigbee/02_improve_network_range_and_stability.html).

## Step 2: Pair and complete interview

1. install a known-good battery;
2. enable permit join for a limited time;
3. factory-reset the exact model according to its instructions;
4. keep it nearby until interview completes;
5. if a battery sensor falls asleep, wake it briefly with its normal button during interview;
6. close permit join after connection.

The official interface and MQTT methods are documented under [Allowing devices to join](https://www.zigbee2mqtt.io/guide/usage/pairing_devices.html). If the process stops, use the [missing-device diagnostic checklist](/articles/pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya/) instead of repeatedly deleting the entity.

## Step 3: Verify fields and units

Wait for several messages after interview and compare them with a reliable reference instrument. Check:

- `temperature` in degrees Celsius;
- `humidity` as relative humidity in percent;
- `battery` or `voltage`, if exposed by the model;
- time of the last message;
- availability and diagnostic `linkquality`.

Do not automate from the first packet. A sensor needs time to equalize after being outdoors or held in warm hands. A small consistent offset can be calibrated, while jumps and frozen values require diagnosis first.

## Step 4: Give the device a durable name

Names should survive equipment moves. `sensor_01` and “left sensor” quickly lose meaning. Combine zone and role, for example “Seedlings · air” or `seedlings_air`. Keep the IEEE address as a technical identifier rather than exposing it in normal dashboards.

GrowerHub assigns the sensor to a room, greenhouse, or grow box so its reading appears with the equipment in that zone instead of being mixed into a farm-wide average.

## Step 5: Choose the installation location

For plants, place the sensor around leaf height, in shade, away from a fan jet, lamp, humidifier, or wet tray. One sensor rarely describes both ends of a long greenhouse. Compare locations before treating them as one zone.

Observe the sensor for 24–48 hours and compare its trend with lighting, ventilation, and watering events. A sharp step with no physical cause, a perfectly flat line, or regular gaps should be investigated.

## Step 6: Configure availability and freshness

Zigbee2MQTT treats powered and sleeping devices differently. A battery sensor cannot be polled like a smart plug. The standard behavior and timeouts are documented under [Device Availability](https://www.zigbee2mqtt.io/guide/configuration/device-availability.html).

For control, also define a maximum age for the actual measurement. A ventilation rule may require fresher temperature data than a weekly report. When a value is stale, report the problem and block the new action.

## If the sensor joined but data is wrong

| Symptom | Check |
|---|---|
| temperature or humidity is always zero | raw payload, exact-model page, and whether configure/interview completed |
| values appeared once and never changed | battery, `last_seen`, availability, and the model's normal reporting interval |
| humidity looks like temperature, or vice versa | actual property names in `exposes`, not an old dashboard label |
| a second entity appeared after renaming | friendly-name and discovery identifier stability before deleting the old entity |
| regular gaps occur only at the final location | powered Zigbee routers, 2.4 GHz interference, and coordinator placement |

Verify the raw Zigbee2MQTT payload first, then inspect GrowerHub or Home Assistant. This separates a device problem from an application-mapping problem.

## Limitations

A consumer Zigbee sensor is not an industrial instrument and often lacks condensation protection. Do not place it where water reaches the enclosure. Critical ventilation requires independent limits and confirmation of the physical result, not only a relay command.

The [farm automation page](/avtomatizatsiya-mini-fermy/#demo-ekrany) shows how GrowerHub keeps zone readings and freshness visible.

## The sensor is ready when

- the exact model and `exposes` are verified;
- pairing and interview completed without errors;
- temperature and humidity are plausible;
- the name identifies zone and role;
- reports remain stable after moving the device;
- availability and data age are part of control rules;
- the enclosure is away from direct heat, water, and airflow.

Only then should the reading participate in GrowerHub, Home Assistant, or another automation. If you are still choosing hardware, see the [sensor examples](/oborudovanie/datchiki/) and verify the exact model ID. A first GrowerHub setup follows the [short connection path](/kak-nachat/) without a crop questionnaire.
