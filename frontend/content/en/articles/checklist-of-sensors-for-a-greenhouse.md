---
translation_of: sensor-checklist
slug: checklist-of-sensors-for-a-greenhouse
title: Checklist of sensors for a greenhouse
summary: >-
  A minimum set of sensors for a greenhouse and automatic watering:
  microclimate, soil moisture, leakage, water consumption, light and
  communication diagnostics.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - sensors
  - greenhouse
keywords:
  - greenhouse sensors
  - Zigbee temperature humidity sensor
  - soil moisture sensor
related:
  - zigbee-dlya-teplitsy-kakie-ustroystva-polezny
  - podklyuchit-zigbee-datchik-temperatury-vlazhnosti
  - datchik-vlazhnosti-pochvy-dlya-avtopoliva
  - zigbee-datchik-protechki-dlya-avtopoliva
hero_image: /content/articles/illustrations/sensor-checklist.webp
hero_alt: >-
  Illustration GrowerHub: checklist of greenhouse sensors for microclimate and
  irrigation
---
![Illustration GrowerHub: checklist of greenhouse sensors for microclimate and irrigation](/content/articles/illustrations/sensor-checklist.webp)

Sensors in a greenhouse are needed not for collecting readings, but for making decisions. A good checklist starts with questions: what could go wrong, how quickly it needs to be noticed, and what action will follow. If the sensor does not affect care, notification, or reporting, it can be deferred.

## Air temperature and humidity

This is a basic microclimate sensor. Place it at leaf level, in the shade, not near a lamp or under direct fan flow. For a long greenhouse, one sensor may not be enough. For multiple zones, it is better to have separate points than to average different conditions.

If you are using Zigbee, check the update rate, battery and availability. The connection is discussed in the article [Zigbee-temperature and humidity sensor](/articles/podklyuchit-zigbee-datchik-temperatury-vlazhnosti).

## Soil moisture

The soil moisture sensor helps to see the dynamics of irrigation, but does not replace inspection. It measures a point around the probe. Place it not near the dropper or against the wall of the pot. Before automatic watering, use the sensor only for graphs for a few days.

Installation and thresholds are discussed in more detail in the article [soil moisture sensor for automatic watering](/articles/datchik-vlazhnosti-pochvy-dlya-avtopoliva).

## Leak

A leakage sensor is a mandatory element next to an automatic watering system. It is placed in the path of possible water: under a tank, pump, pan or connection. Its activation should turn off the pump and block new watering until it is checked.

Practical installation locations are described in the article [Zigbee-leakage sensor](/articles/zigbee-datchik-protechki-dlya-avtopoliva).

## Light, water consumption and power

The light sensor is useful if you are comparing growth to lighting or controlling additional lighting. Water flow is needed for reporting and leak detection. Monitoring the power and status of outlets helps to understand why the pump or light did not work.

## Communication diagnostics

For wireless sensors, look not only at the value, but also at the freshness of the data. An inaccessible sensor can be more dangerous than low humidity: the system does not know the real condition. In GrowerHub availability should affect notifications and rules.

## Conclusion

Minimum set: microclimate, soil moisture, leakage, irrigation status and communication diagnostics. Add the rest when it is clear what solution the new sensor will provide. This way the greenhouse remains manageable and not overloaded with readings.
