---
translation_of: avtomatizatsiya-sveta-v-groubokse
slug: automation-of-light-in-a-grow-box-schedule-relays-and-protection
title: 'Automation of light in a grow box: schedule, relays and protection'
summary: >-
  How to automate lighting in a grow room without dangerous overloads: schedule,
  relays, smart sockets, manual mode and connection with a care log.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: avtopoliv-i-kontroller-vyrashchivaniya
tags:
  - GrowerHub
  - grow box
  - light
keywords:
  - automation of light in the grow box
  - lighting relay
  - grow box automation
related:
  - zigbee-rozetka-dlya-sveta-i-nasosa
  - ventilyatsiya-i-vytyazhka-dlya-mikroklimata
  - kontrol-mikroklimata-v-teplitse
  - dnevnik-rasteniya-chto-zapisyvat
hero_image: /content/articles/illustrations/avtomatizatsiya-sveta-v-groubokse.webp
hero_alt: 'Illustration GrowerHub: grow box, lamp, socket, day and night schedule'
---
![Illustration GrowerHub: grow box, lamp, socket, day and night schedule](/content/articles/illustrations/avtomatizatsiya-sveta-v-groubokse.webp)

Automating the light in a grow room seems simple: turn on the lamp in the morning, turn it off in the evening. In practice, three things are important: a stable schedule, safe load management and a clear manual mode. Plants need repeatable daylight hours, and the owner needs confidence that the relay is not overloaded and the lamp does not remain on after maintenance.

Before automation, check the rated power of the lamp, the driver starting currents and the permissible load of the socket or relay. You cannot select a device only based on the inscription “up to 16 A” if it will operate in a warm, closed box with a constant load. For high-power fixtures, it is better to use a high-quality relay or contactor, and leave the smart socket for monitoring or light load.

## Keep the schedule simple

It is better to keep the light according to an understandable regime, and not change it every day. For most neutral house crops and seedlings, stability is important: the plant gets used to the cycle, and the owner notices problems more easily. In GrowerHub, it is convenient to link the light schedule with the care log: if you change the mode, this should be visible next to watering, fertilizing and photos.

Complex scenarios like “light depends on cloud cover” should be added only after basic stability. In a room with a window this can be useful, but in a closed box the main light source is usually artificial. There, the more important thing is not smart adaptation, but precise switching on and off.

## Relay, socket and security

If the lamp is connected through a smart plug, check whether it supports the required power and how it behaves after a power loss. Some devices return to their last state, others remain turned off, and others depend on the hub. This is important for plants: accidentally turning off the light for the whole day has a noticeable effect on growth.

The material [Zigbee-socket for light and pump](/articles/zigbee-rozetka-dlya-sveta-i-nasosa) examines the limitations of such devices. The main rule: automation should not be the weak point of electrics. Do not place connections under possible leakage, do not overload extension cords, or hide hot power supplies in a tight, enclosed space without ventilation.

## Relationship with light, temperature and ventilation

When the lamp turns on, the temperature rises. Therefore, light cannot be considered separately from ventilation. If, after turning on the lamp, the temperature rises above the operating range, the controller should at least send a notification, and ideally turn on the hood or increase the air exchange. This is especially noticeable in compact boxes.

It is useful to compare graphs of light, temperature and humidity. If every day an hour after turning on the lamp the humidity drops sharply, watering and ventilation may require adjustments. The approach to air exchange is described in the article [ventilation and exhaust for microclimate](/articles/ventilyatsiya-i-vytyazhka-dlya-mikroklimata).

## Manual mode and maintenance

The light should have a clear manual mode. For example, you opened the plant treatment box and turned on the lamp outside the schedule. The system should indicate that this is a manual state, and after a specified time, return to normal mode or remind about it. Without this, it's easy to leave the lights on overnight or off after checking.

In the GrowerHub log it is worth recording changes to the schedule: date, old mode, new mode and reason. This helps to understand the height from the photo. If the plant has stretched, changed color or stopped, it is useful to see not only the current light, but also the history of the last weeks. The article [plant diary](/articles/dnevnik-rasteniya-chto-zapisyvat) is useful for observations.

## Conclusion

Light automation should be boring and reliable. A stable schedule, properly selected relay, overload protection, communication with ventilation and a noticeable manual mode provide more benefits than complex effects. In GrowerHub, the light should be kept close to watering and microclimate: then changes in care are visible in one context.
