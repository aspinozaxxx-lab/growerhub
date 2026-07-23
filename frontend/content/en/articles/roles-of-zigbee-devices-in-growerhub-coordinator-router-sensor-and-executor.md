---
translation_of: roli-zigbee-ustroystv-v-growerhub
slug: zigbee-device-roles-in-growerhub-coordinator-router-sensor-and-actuator
title: 'Zigbee device roles in GrowerHub: coordinator, router, sensor and actuator'
summary: >-
  The roles Zigbee devices play and why coordinators, routers, battery sensors
  and actuators matter in a reliable greenhouse.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - Zigbee
  - net
keywords:
  - Zigbee Hub
  - Zigbee relay
  - compatibility Zigbee2MQTT
related:
  - zigbee2mqtt-prostymi-slovami
  - zigbee-dlya-teplitsy-kakie-ustroystva-polezny
  - zigbee-rozetka-dlya-sveta-i-nasosa
  - pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya
hero_image: /content/articles/illustrations/roli-zigbee-ustroystv-v-growerhub.webp
hero_alt: >-
  Illustration GrowerHub: roles of Zigbee network, coordinator, router, sensor
  and relay
---
![Illustration GrowerHub: Zigbee-network roles, coordinator, router, sensor and relay](/content/articles/illustrations/roli-zigbee-ustroystv-v-growerhub.webp)

In the Zigbee network, devices perform different roles. If you do not distinguish between them, it is easy to build an unstable system: place sensors far from the hub, wait for the battery device to strengthen the network, or control the pump through the outlet without checking the connection. For GrowerHub, roles are important not theoretically, but practically: the freshness of data and the reliability of actions depend on them.

Basic roles: coordinator, router, end device and executor. One physical device can combine several functions, for example, a Zigbee socket controls the load and simultaneously routes the network. But a battery temperature sensor usually only sends its data and does not help others.

## Coordinator

The coordinator creates a Zigbee network and connects it to the hub or Zigbee2MQTT. Usually this is a USB adapter, a built-in module or a ready-made Zigbee Hub. The network depends on the coordinator, but he does not have to be directly in the greenhouse. It is important to position it so that there are stable routers nearby and there is no strong interference.

If the coordinator is overloaded, poorly firmed, or connected through a noisy USB port, problems will appear as random devices disappearing. Therefore, for GrowerHub it is better to use a proven coordinator and not change it unless necessary.

## Routers

Routers transmit messages to other devices. Typically these are mains-powered sockets, lamp modules, relays or special repeaters. In a greenhouse, routers are especially important because distance, walls, metal and humidity reduce communication. One hub in an apartment does not always reach all sensors.

The router must be constantly turned on. If you use the outlet as a router and periodically remove it from the network, the route may break. For reliability, it is better to have several powered devices between the coordinator and the distant sensors.

## End devices

Battery temperature, humidity and leakage sensors are most often end devices. They sleep, wake up, send data and save battery again. Because of this, they are not suitable as repeaters and sometimes do not immediately respond to commands.

This is normal for plants: the microclimate sensor does not have to be constantly active. But GrowerHub must see the freshness of the data. If the sensor has not been updated for a long time, the automatic decision on it should be cautious.

## Performers

Actuators include something: pump, light, fan, valve. For them, not only the Zigbee signal and command are important, but also electrical safety, load, behavior after a power failure and emergency restrictions. The material [Zigbee-socket for light and pump](/articles/zigbee-rozetka-dlya-sveta-i-nasosa) examines this in more detail.

## Conclusion

A reliable Zigbee system for GrowerHub starts with understanding the roles. The coordinator creates the network, routers maintain coverage, sensors provide data, performers change the physical state. If each role is covered correctly, the greenhouse and box become more predictable, not just smarter.
