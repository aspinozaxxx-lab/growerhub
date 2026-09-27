---
translation_of: gotovyy-zigbee-hub-dlya-growerhub
slug: ready-zigbee-hub-for-growerhub-when-it-is-more-convenient-than-self-assembly
title: 'A ready-made Zigbee hub for GrowerHub: starting without a separate computer'
summary: >-
  Start with one sensor: existing Zigbee2MQTT, the PushOk pilot and SMLIGHT
  SMHUB Nano. Connection options and what to check before buying hardware.
created_at: '2026-07-23'
updated_at: '2026-09-27'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - Zigbee Hub
  - hub
keywords:
  - Zigbee Hub
  - ready Zigbee Hub
  - GrowerHub Zigbee
related:
  - zigbee2mqtt-prostymi-slovami
  - zigbee-ili-wifi-datchiki-dlya-rasteniy
  - roli-zigbee-ustroystv-v-growerhub
  - lokalnaya-avtomatizatsiya-bez-oblaka
hero_image: /content/articles/illustrations/gotovyy-zigbee-hub-dlya-growerhub.webp
hero_alt: >-
  Illustration of GrowerHub: ready-made Zigbee Hub, plant sensors and local
  network
---
![Illustration GrowerHub: ready Zigbee Hub, plant sensors and local network](/content/articles/illustrations/gotovyy-zigbee-hub-dlya-growerhub.webp)

One temperature sensor beside your plants is enough for a first setup. You can start by [opening the demo farm without hardware](/app/demo/?lang=en), exploring charts and trying virtual watering before buying a pump or a complete greenhouse kit.

GrowerHub already receives physical sensor data through Zigbee2MQTT. A Zigbee logo alone does not establish compatibility: the hub's software and connection method matter.

## Choose a starting point

- **Zigbee2MQTT already runs, perhaps alongside Home Assistant.** Keep your hub and connect the existing network through a [local MQTT bridge](/en/articles/growerhub-and-home-assistant-via-mqtt-practical-integration-scheme/). Having Home Assistant does not necessarily mean you use Zigbee2MQTT.
- **An always-on computer is available.** Use a [USB coordinator and one compatible sensor](/en/equipment/zigbee-coordinators/). Zigbee2MQTT runs on that computer; new readings stop reaching GrowerHub when it is off.
- **You want one box without a separate computer.** Consider a hub that runs Zigbee2MQTT internally. The candidate below needs a trial connection before we can recommend it as a tested GrowerHub setup.
- **You already own PushOk.** You can request a pilot. The GrowerHub integration is not ready yet.

## SMLIGHT SMHUB Nano: the computer is inside

According to [SMLIGHT's product documentation](https://smlight.tech/products/smhub-nano-mg24), **SMHUB Nano MG24** runs Zigbee2MQTT on the hub and is configured in a browser. A separate Home Assistant installation or Raspberry Pi is unnecessary. SMLIGHT documents [connecting it to an external MQTT broker](https://smlight.tech/support/manuals/books/smhub/page/connecting-zigbee2mqtt-on-smhub-to-home-assistant).

That makes it a promising candidate for GrowerHub's existing Zigbee2MQTT connection. **We have not tested a physical SMHUB Nano with GrowerHub.** Encrypted connectivity and settings persistence after a restart still need device testing. [Discuss your proposed kit and trial connection](/en/getting-started/) before purchasing.

As a price reference, [Domadoo lists the hub at €55.99](https://www.domadoo.fr/en/smart-home-products/8664-smlight-smhub-nano-mg24-hub-running-linux-zigbee2mqtt-node-red-and-matterbridge.html), checked on 27 September 2026. This is a retailer's hub price, not a delivered kit quote for your country. Check the sensor, power supply, cables and shipping separately.

### The proposed first connection

This sequence is for a **new, separate SMHUB network** and remains to be verified in a pilot:

1. Connect power and your home network, then open the hub's page in a browser.
2. Create a connection in GrowerHub. Copy its MQTT server, username, password, base topic and Client ID into the hub's Zigbee2MQTT settings. The server address alone is not enough.
3. Pair one sensor using its manufacturer's instructions.
4. Assign the readings to a greenhouse or home growing area in GrowerHub. Check that measurement times advance and history appears.

Transfer only the MQTT settings. Replacing the entire hub configuration with a USB-coordinator template can overwrite its radio and web-interface settings. Keep the encrypted connection: [Zigbee2MQTT supports MQTT over TLS](https://www.zigbee2mqtt.io/guide/configuration/mqtt.html).

If the SMHUB already serves Home Assistant or other systems, plan a local bridge first: changing its MQTT server may interrupt existing integrations. A GrowerHub connection forwards the selected network's device list and readings and permits commands back to it. There is currently no separate read-only mode limited to one sensor. Automations are enabled separately by the user.

## PushOk: request a pilot first

PushOk owners can [register interest in a pilot](/en/getting-started/#pushok). In the app, open **Settings → Connections → Connect PushOk**, choose to participate and leave your preferred contact method.

Submitting the request does not connect or change your hub. We will review its model, sensors and connection options together. A phone-only setup without a separate server is still being investigated; do not buy PushOk expecting an already available GrowerHub integration.

## Distinguish the different types of hub

**SMHUB Nano and SLZB-06 are different products.** The standalone Zigbee Hub mode on SLZB uses [its own MQTT API](https://smlight.tech/support/manuals/books/slzb-os/page/mqtt-api), rather than Zigbee2MQTT's format. Its documentation also specifies TCP-only connections. Entering the GrowerHub address in that mode is not sufficient.

For Tuya, Aqara and other hubs, check the exact model and available integration. Labels such as Zigbee 3.0, MQTT or works with Home Assistant do not replace this check. Verify the sensor's exact model in the [Zigbee2MQTT device catalogue](https://www.zigbee2mqtt.io/supported-devices/) as well.

## From a first chart to automation

Start by observing temperature or humidity. Once readings arrive reliably, choose one useful automation and verify its operation. GrowerHub automations run on the server and require connectivity to it; a hub's local capabilities do not make GrowerHub automations run locally.

For watering, separately check the specific valve or pump and how it stops after a connection failure. Read the [automatic watering safety guide](/en/articles/safe-automatic-watering-water-limits-pauses-and-emergency-stop/) before enabling it. If you do not own hardware yet, try the [demo farm](/app/demo/?lang=en) and choose a kit around one useful task.
