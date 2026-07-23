---
translation_of: sovmestimost-zigbee2mqtt-exposes-availability
slug: zigbee2mqtt-compatibility-exposes-availability-and-device-capabilities
title: 'Zigbee2MQTT compatibility: exposes, availability and device capabilities'
summary: >-
  How to check the compatibility of a Zigbee device in Zigbee2MQTT: what fields
  it sends, what commands it accepts and how to control availability.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - Zigbee2MQTT
  - compatibility
keywords:
  - compatibility Zigbee2MQTT
  - Zigbee2MQTT exposes
  - Zigbee availability
related:
  - zigbee2mqtt-prostymi-slovami
  - podklyuchit-zigbee-datchik-temperatury-vlazhnosti
  - pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya
  - mqtt-discovery-home-assistant
hero_image: >-
  /content/articles/illustrations/sovmestimost-zigbee2mqtt-exposes-availability.webp
hero_alt: 'Illustration GrowerHub: Zigbee2MQTT exposes, availability and device card'
---
![Illustration GrowerHub: Zigbee2MQTT exposes, availability and device card](/content/articles/illustrations/sovmestimost-zigbee2mqtt-exposes-availability.webp)

Compatibility of a Zigbee device is not only about “connected or not”. For GrowerHub, it is important what data the device actually sends, what commands it receives, and whether it is possible to understand that it is missing. In Zigbee2MQTT, exposes, states and availability are responsible for this. If these are not checked, the automation may rely on a field that a particular model does not have.

Two similar outlets may differ in power measurement, behavior after power failure, and routing quality. Two similar temperature sensors may update humidity and battery differently. Therefore, before purchasing and especially before the automatic scenario, you need to look at the real possibilities.

## What is exposes

Exposes is a list of capabilities that Zigbee2MQTT shows for the device: temperature, humidity, battery, contact, water_leak, state, power, linkquality and other fields. For a plant sensor, it is important that the required parameters are available and updated as expected. For the socket, it is important whether the state can be controlled and diagnostic data can be seen.

If a device has connected but does not return the required field, GrowerHub should not try to guess it. It is better to choose another model or change the scenario. For example, a leak sensor should give a clear leak event, and not just an abstract status.

## Availability

Availability shows whether the device is accessible in terms of Zigbee2MQTT. This is critical for automatic watering. If a moisture or leakage sensor is not available, the system should not make a risky decision based on old data. Need notification and safe condition.

But availability also needs to be understood correctly. Battery-powered devices sleep, so their availability may be judged differently than at a wall outlet. In GrowerHub rules, it is important to consider the time of the last value and the device type, and not just the green or red status.

## Check before script

After pairing, do not immediately include the device in automation. Watch it for several days: whether the parameters are updated, whether the connection is lost, how the battery changes, how the device responds to commands. For the socket, check the switching on and off, for leakage - test with water, for the microclimate sensor - change in values when transferred.

If Home Assistant discovery is used, ensure that entities appear with meaningful names and are not duplicated. MQTT discovery is described separately in the article [MQTT discovery Home Assistant](/articles/mqtt-discovery-home-assistant).

## Names and zones

A compatible device is still useless if its name is not associated with a zone. In GrowerHub it is better to store a clear connection: device, physical location, plant or zone, role, installation date. Then, if the sensor disappears, it is clear what exactly has stopped updating and what rule it affects.

## Conclusion

Check compatibility using real features: exposes, commands, availability, data freshness and behavior after failures. For GrowerHub this is the basis of safe rules. A device that is simply “visible on the network” is not yet ready to control irrigation, lights, or an emergency stop.
