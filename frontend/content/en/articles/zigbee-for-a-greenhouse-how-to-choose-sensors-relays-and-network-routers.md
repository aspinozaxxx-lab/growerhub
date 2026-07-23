---
translation_of: zigbee-dlya-teplitsy-kakie-ustroystva-polezny
slug: zigbee-for-a-greenhouse-how-to-choose-sensors-relays-and-network-routers
title: 'Zigbee for a greenhouse: how to choose sensors, relays and network routers'
summary: >-
  Practical selection of Zigbee devices for the greenhouse: microclimate,
  leakage, sockets, relays, network coverage, compatibility and testing before
  automation.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - Zigbee
  - greenhouse
keywords:
  - Zigbee for greenhouse
  - Zigbee sensors for greenhouse
  - smart greenhouse Zigbee
related:
  - zigbee2mqtt-prostymi-slovami
  - podklyuchit-zigbee-datchik-temperatury-vlazhnosti
  - zigbee-datchik-protechki-dlya-avtopoliva
  - zigbee-rozetka-dlya-sveta-i-nasosa
hero_image: >-
  /content/articles/illustrations/zigbee-dlya-teplitsy-kakie-ustroystva-polezny.webp
hero_alt: >-
  Zigbee-greenhouse network with microclimate sensors, leakage sensors and
  routers
---
![Zigbee-greenhouse network with microclimate sensors, leakage and routers](/content/articles/illustrations/zigbee-dlya-teplitsy-kakie-ustroystva-polezny.webp)

Zigbee is convenient for greenhouses: battery sensors do not require Wi-Fi for each device, and the local network can operate without the manufacturer’s cloud. But a greenhouse is more complex than a room - distance, metal, humidity, condensation and pumps require checking not only functions, but also operating conditions.

## Select by task

| Device | Why put | Home check |
|---|---|---|
| air temperature and humidity | see the microclimate of the zones | accuracy, reporting frequency, installation location |
| leak | stop water in case of accident | real wetting test and delivery time event |
| socket | manage a small suitable load | permissible current, starting current, state after power supply |
| relay or contactor | control light, pump, ventilation | electrical diagram and qualified installation |
| network router Zigbee | close the far or screened zone | route stability in the workplace |

Don't start with a list of gadgets. Draw zones and mark where information is needed, where the command is, and where emergency protection is.

## 1. Microclimate sensors

For a short greenhouse, start at two points if conditions at the entrance and at the back are different. Place the sensor at leaf level, in the shade, and not near a heater, door, nozzle or direct fan flow. Compare zones for several days before averaging them.

Before purchasing, open the exact model in [catalog Zigbee2MQTT](https://www.zigbee2mqtt.io/supported-devices/) and make sure that the necessary `temperature`, `humidity`, battery and clear update behavior are available. Step-by-step connection is discussed in the material [how to connect a Zigbee temperature and humidity sensor](/articles/podklyuchit-zigbee-datchik-temperatury-vlazhnosti/).

## 2. Leakage sensors

Place them in places where water will appear first: under the reservoir, pump, manifold, connections and pan. A leak should not only send a message, but also put the system into a safe state. After installation, perform a physical test and check the event recording.

The wireless sensor does not replace mechanical protection, pan, working connections and limited water supply. The larger the tank, the higher the cost of one mistake.

## 3. Sockets and relays

A Zigbee network device can simultaneously manage load and route messages, but these roles do not guarantee quality. For each device, check:

- exact permissible load and starting current;
- whether the housing is suitable for the installation location;
- what happens after the power is turned off and returned;
- whether the actual state of the relay is transmitted;
- Is it possible to safely turn off the equipment manually?

A pump or a powerful light cannot be selected based on the “16 A” inscription on the product card. Mains voltage near water requires qualified installation; Often the Zigbee relay must control the contactor, and not the load itself.

## 4. Build your network before automation

First install a coordinator and several high-quality routers, then connect long-range battery sensors. Remove the USB coordinator from your computer and Wi‑Fi equipment. Zigbee2MQTT specifically recommends USB extender, 2.4 GHz interference analysis and network routers: [Improve network range and stability](https://www.zigbee2mqtt.io/advanced/zigbee/02_improve_network_range_and_stability.html).

Check not only the network map, but also messages per day. The route may change and `linkquality` may be high at the time of testing and unstable at night.

## 5. Test matrix

Before enabling scripts, go through the table:

| Test | Expected result |
|---|---|
| the sensor is moved to the operating point | data continues to arrive with normal frequency |
| router Zigbee is disabled | the system shows a loss of connection and does not run a dangerous command |
| leak triggered | pump stopped, new irrigation blocked, event recorded |
| relay power lost | upon return, it goes into a pre-selected safe state |
| the sensor has not been updated for a long time | the interface shows outdated data, the script does not consider it normal |

Availability settings for active and battery-powered devices are different; refer to [official documentation Zigbee2MQTT](https://www.zigbee2mqtt.io/guide/configuration/device-availability.html).

## How to assemble this in GrowerHub

In GrowerHub, devices are linked to rooms and boxes, so the operator sees not a technical list, but the state of the zone: microclimate, light, ventilation, watering and data freshness. An example is shown on the [mini-farm automation](/avtomatizatsiya-mini-fermy/#demo-ekrany) page.

## Restrictions

- Zigbee does not guarantee the moisture protection of the case.
- The battery sensor is usually not a router.
- Compatibility is checked based on the exact model and revision.
- The radio network does not replace feedback on the actual operation of the pump.
- Critical restrictions must be maintained when the Internet and interface are lost.

For the first stage, a microclimate, leakage and a stable coating are sufficient. Add control after the data has consistently survived several normal days and one controlled failure.

You can assemble a starter set using three separate lists: [coordinators](/oborudovanie/zigbee-koordinator/), [sensors](/oborudovanie/datchiki/) and [sockets](/oborudovanie/zigbee-rozetki/). Links lead to a search for the exact model without affiliate tags; Always check the revision before purchasing.
