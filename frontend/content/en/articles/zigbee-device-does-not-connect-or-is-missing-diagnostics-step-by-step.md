---
translation_of: pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya
slug: zigbee-device-does-not-connect-or-is-missing-diagnostics-step-by-step
title: 'Zigbee device does not connect or is missing: diagnostics step by step'
summary: >-
  What to check if the Zigbee device is not found, has disappeared from the
  network or does not connect after removal: reset, permit join, power, logs,
  availability and coverage.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - Zigbee
  - pairing
keywords:
  - Zigbee device does not connect
  - Zigbee device disappeared
  - pairing Zigbee
  - Zigbee2MQTT
related:
  - podklyuchit-zigbee-datchik-temperatury-vlazhnosti
  - sovmestimost-zigbee2mqtt-exposes-availability
  - roli-zigbee-ustroystv-v-growerhub
  - zigbee2mqtt-prostymi-slovami
hero_image: >-
  /content/articles/illustrations/pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya.webp
hero_alt: Diagnostics of a Zigbee device that does not connect to the coordinator
---
![Diagnostics of a Zigbee device that does not connect to the coordinator](/content/articles/illustrations/pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya.webp)

If a Zigbee device is not connecting, first identify the scenario: it **never appeared on the network**, **used to work and disappeared**, or **stopped connecting after being removed**. These cases require different actions. Repeatedly pressing a button and repeatedly deleting an entry usually only erases useful features.

## Quick diagnostic table

| Symptom | Probable Cause | First check |
|---|---|---|
| There is no response to reset in the log | the device has not entered pairing mode, the battery is low | instructions for the exact model, new battery, try next to the coordinator |
| An interview started in the log, then an error | weak connection, device has fallen asleep, model is partially supported | wake up the battery device, bring it closer, check the model page |
| The device is connected, but the required field is missing | another revision or incomplete support | `model`, `manufacturer`, list `exposes` |
| It worked and became `offline` | power, coverage, network router or radio interference | last message, battery, neighboring routers, coordinator position |
| Can't be found after deletion | the device remembers the old network | factory reset, then new permit join |

## If the device has never been connected before

1. Write down the exact model from the case and check it in [official device list Zigbee2MQTT](https://www.zigbee2mqtt.io/supported-devices/). A similar store name does not guarantee the same electronics.
2. Install a known-good battery or check the power of the network device.
3. Bring the device 0.5–1 meter closer to the coordinator. The first connection at a workplace in a distant greenhouse complicates the diagnosis.
4. Open permit join for a limited time. In interface Zigbee2MQTT the network opens for 254 seconds; you can choose to connect through a specific router.
5. Perform a factory reset strictly according to the model instructions. The sign of pairing is usually a separate flashing sequence, rather than just an LED turning on.
6. See log. A successful join must reach a completed interview, after which the model and available properties appear.

The official procedure is described in the instructions [Allowing devices to join](https://www.zigbee2mqtt.io/guide/usage/pairing_devices.html). If the interview has started, but the battery sensor has fallen asleep, briefly activate it with the button during the interview without performing a new reset.

## If the device was working and disappeared

Don't delete it right away. First save friendly name, model, last message time and power status. Then check in order:

1. does Zigbee2MQTT itself work and does the coordinator see other devices;
2. does the missing device have power or a fresh battery;
3. whether the network router through which the route went was turned off;
4. whether there is a Wi‑Fi router, SSD, USB 3.0 or other 2.4 GHz interference source nearby;
5. Whether the device returns after being activated by a button or briefly removing the power.

`linkquality` is useful as a comparative indicator, but one number does not prove the quality of the route. Much more important is the repetition of messages in the workplace. Zigbee2MQTT recommends removing the USB coordinator from the computer with a shielded extension cord and, if necessary, adding high-quality network routers: [improving range and stability](https://www.zigbee2mqtt.io/advanced/zigbee/02_improve_network_range_and_stability.html).

## How to read availability correctly

When the Zigbee2MQTT function is enabled, publishes `online` or `offline` to the device availability topic. Active network devices and sleeping battery sensors are checked differently: the battery sensor is not required to send data every ten minutes. Therefore, a user timeout that is too short creates false alarms.

Check the settings and default behavior in the [Device Availability](https://www.zigbee2mqtt.io/guide/configuration/device-availability.html) documentation. For the watering scenario, additionally set your own acceptable freshness of a specific measurement: the old humidity value cannot be considered normal just because the device is not yet marked with `offline`.

## After being removed from the network

Deleting an entry in Zigbee2MQTT does not always reset the device itself. It may continue to remember the old network. The sequence is:

1. close the old permit join;
2. perform a factory reset of the device;
3. open a new permit join;
4. keep the device near the coordinator until the interview is completed;
5. check the real values and only then transfer it to the work area.

After the migration, wait for a few normal update cycles. If the sensor disappears again only in the greenhouse, the pairing is OK - the problem is the coating or interference.

## When a device cannot be included in automation

Do not associate it with a pump, light or ventilation if the interview does not complete, the required property is sometimes missing, data is jumping for no physical reason, or availability is not checked. First achieve stable monitoring. An example of how GrowerHub shows zones and data freshness is on the [mini-farm automation](/avtomatizatsiya-mini-fermy/#demo-ekrany) page.

## Final checklist

- exact model supported;
- food is correct;
- reset was performed according to the instructions;
- permit join is open for the duration of the connection;
- interview completed;
- real `exposes` verified;
- after the transfer, messages arrive steadily;
- old data blocks dangerous scripts.

If you go through the points in this order, it becomes clear what exactly is broken: the connection, model support, or the working Zigbee network.

For a new circuit, start with [soft recommendations on coordinators](/oborudovanie/zigbee-koordinator/) and [sensors](/oborudovanie/datchiki/). After logging in, GrowerHub will open permit join for three minutes and automatically show devices that have completed the interview.
