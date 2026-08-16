---
translation_of: "ustanovka-zigbee2mqtt-na-windows"
slug: "install-zigbee2mqtt-on-windows-usb-coordinator"
title: "Install Zigbee2MQTT on Windows with a USB coordinator"
summary: "A step-by-step Zigbee2MQTT setup for Windows: USB coordinator, COM port, Z-Stack or Ember, GrowerHub configuration, startup and troubleshooting."
created_at: "2026-08-16"
updated_at: "2026-08-16"
cluster: "zigbee-hub-i-ustroystva"
tags:
  - "GrowerHub"
  - "Zigbee2MQTT"
  - "Windows"
keywords:
  - "install Zigbee2MQTT Windows"
  - "Zigbee2MQTT Windows USB coordinator"
  - "Zigbee2MQTT COM port"
related:
  - "zigbee2mqtt-prostymi-slovami"
  - "gotovyy-zigbee-hub-dlya-growerhub"
  - "podklyuchit-zigbee-datchik-temperatury-vlazhnosti"
  - "pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya"
hero_image: "/content/articles/illustrations/zigbee2mqtt-prostymi-slovami.webp"
hero_alt: "Zigbee2MQTT on Windows with a USB coordinator, MQTT and GrowerHub"
---

Zigbee2MQTT can run on an ordinary always-on Windows computer. A USB coordinator with coordinator firmware and one Zigbee sensor are enough for a first setup. GrowerHub provides an isolated, encrypted MQTT connection, so a new direct installation does not require a local MQTT broker.

The shortest path is to download the GrowerHub Windows package and two personal configuration files from the dashboard. This guide also explains where to look when Windows cannot see the USB stick or Zigbee2MQTT does not come online.

## What you need

| Component | Minimum setup | What to verify |
|---|---|---|
| host | an always-on Windows 10/11 computer | stable internet and a free USB port |
| coordinator | a USB adapter supported by Zigbee2MQTT | coordinator firmware and adapter type |
| runtime | a current Node.js LTS release with Corepack | `node --version` and `corepack --version` work |
| first device | a Zigbee temperature and humidity sensor | the exact model is in the compatibility catalog |
| GrowerHub | a free account | a connection exists and both configuration files are saved |

The official Zigbee2MQTT Windows guide currently specifies Node.js 22 LTS. Check the [current Windows instructions](https://www.zigbee2mqtt.io/guide/installation/05_windows.html) before installing because the required LTS release changes over time.

## Step 1: Identify the coordinator type

Choose the adapter type from its chipset and firmware, not its enclosure:

- `zstack` for SONOFF ZBDongle-P and compatible CC2652/CC1352 coordinators;
- `ember` for SONOFF ZBDongle-E and compatible Silicon Labs coordinators;
- for another model, find it in the [official adapter list](https://www.zigbee2mqtt.io/guide/adapters/) and use the documented value.

An inexpensive CC2652P stick is suitable when it contains coordinator firmware. A USB extension cable also helps move the radio away from the computer chassis, USB 3.0, and other interference sources.

## Step 2: Create a GrowerHub connection

1. Sign in to GrowerHub and open the first-connection wizard.
2. Create a coordinator with any clear name.
3. Select “New installation” and Windows.
4. Download `configuration.yaml` and `secret.yaml` immediately; the MQTT password is displayed once.
5. Open the installation packages and download the latest Windows ZIP from [GitHub Releases](https://github.com/aspinozaxxx-lab/growerhub/releases).

The ZIP contains no personal credentials. Do not publish `secret.yaml` or send it in chat. If it is lost, rotate the credentials in the dashboard.

## Step 3: Find the COM port

Connect the USB stick and open Device Manager, then expand “Ports (COM & LPT).” Note the port shown beside the USB serial device, for example `COM4`.

If no port appears:

1. try another USB port;
2. test the stick on another computer;
3. install the VCP driver for its USB-to-UART chipset from Silicon Labs, FTDI, or WCH;
4. confirm that the cable or extension carries data rather than power only.

The [supported-adapter documentation](https://www.zigbee2mqtt.io/guide/adapters/) lists the same driver families.

## Step 4: Prepare and run the package

1. Extract the ZIP to a permanent directory such as `C:\GrowerHub\zigbee-coordinator`.
2. Put `configuration.yaml` and `secret.yaml` in the `data` subdirectory.
3. Run `setup-coordinator.bat`.
4. Select the detected COM port and either `zstack` or `ember`.
5. Run `start-coordinator.bat`.
6. Check it with `status-coordinator.bat`.

Do not run two Zigbee2MQTT instances against one USB coordinator. Only one process can own the serial port.

## Step 5: Wait for ONLINE

The package connects to `growerhub.ru:8883` over MQTTS, and the dashboard polls its status automatically. Once it is `ONLINE`, enable joining for three minutes and put the sensor into pairing mode near the coordinator.

Wait for measurements and capabilities to appear before moving the sensor to its final location. The [Zigbee temperature and humidity sensor guide](/articles/podklyuchit-zigbee-datchik-temperatury-vlazhnosti/) covers the full verification sequence.

## If the coordinator stays offline

| Symptom | Check first |
|---|---|
| `No valid USB adapter found` | COM port and `adapter`; see the official [adapter settings](https://www.zigbee2mqtt.io/guide/configuration/adapter-settings.html) |
| `Access denied` or a busy port | close another Zigbee2MQTT instance, flashing tool, or serial terminal |
| the process runs but GrowerHub reports OFFLINE | both YAML files, Windows system time, and outbound access to `growerhub.ru:8883` |
| settings disappear after a restart | run from a permanent directory and preserve the `data` folder |
| a sensor will not pair | pair near the coordinator, factory-reset the device, verify power, and read the Zigbee2MQTT log |

The local package interface is available only on that computer at `http://127.0.0.1:8080`. It does not need to be exposed to the internet.

## If Zigbee2MQTT already works

Do not create a second Zigbee network or move every device just for GrowerHub. Select “Already using Zigbee2MQTT / Home Assistant” in the wizard. The directed connector keeps local MQTT in place and forwards only the required states and commands. See [GrowerHub and Home Assistant over MQTT](/articles/growerhub-i-home-assistant-cherez-mqtt/) for the architecture.

## The setup is complete when

- Windows detects the coordinator on a stable COM port;
- `zstack` or `ember` matches the adapter;
- `status-coordinator.bat` reports a running process;
- the GrowerHub coordinator is `ONLINE`;
- the first device appears with its model, capabilities, and fresh readings;
- the `data` directory remains intact after a computer restart.

You can now create a named zone and assign the device. Plants and automations are optional next steps.
