---
translation_of: avtopoliv-dlya-rasteniy-kak-vybrat
slug: >-
  automatic-watering-for-plants-how-to-choose-a-system-for-pots-boxes-or-greenhouses
title: >-
  Automatic watering for plants: how to choose a system for pots, boxes or
  greenhouses
summary: >-
  Practical analysis of the choice of automatic watering: when a simple timer is
  enough, when a humidity sensor is needed, how to evaluate the pump, reservoir,
  tubes and overflow protection.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: avtopoliv-i-kontroller-vyrashchivaniya
tags:
  - GrowerHub
  - automatic watering
  - plants
keywords:
  - automatic watering for plants
  - automatic watering of indoor plants
  - automatic watering for greenhouses
related:
  - kontroller-poliva-protiv-taymera
  - datchik-vlazhnosti-pochvy-dlya-avtopoliva
  - zigbee-datchik-protechki-dlya-avtopoliva
  - lokalnaya-avtomatizatsiya-bez-oblaka
hero_image: /content/articles/illustrations/avtopoliv-dlya-rasteniy-kak-vybrat.webp
hero_alt: >-
  Illustration GrowerHub: plants, humidity sensor, controller, pump and water
  tank
---
![Illustration GrowerHub: plants, humidity sensor, controller, pump and water tank](/content/articles/illustrations/avtopoliv-dlya-rasteniy-kak-vybrat.webp)

It is better to choose automatic watering not by a beautiful application or by the number of modes, but by what exactly should stop depending on a person’s memory. For three pots on the windowsill, one logic is enough. For a grow box, seedlings on a rack or a small greenhouse, other things are important: water supply, pump behavior, sensors, emergency stop and a clear event log.

The main mistake when choosing is to start with a set of equipment. It would be more correct to first describe the irrigation zone. How many plants are in one zone, do they have the same substrate, is there a common tray, how quickly does the soil dry out on a hot day, where can you put a container of water and what happens if the pump turns on again. Answers to these questions immediately eliminate half of the unsuitable solutions.

## Describe the zone first

The automatic watering zone is not necessarily the entire greenhouse or the entire shelf. This is a group of plants that can be watered with one rule without noticeable risk. If an adult tomato, a tray of seedlings and a small pot of basil stand nearby, it is inconvenient to combine them into one line: one plant doesn’t have enough water, the other has too much water. For this situation, it is better to separate the lines or leave some of the care manual.

For each zone, it is useful to write down four parameters: the approximate volume of the substrate, the usual frequency of manual watering, the maximum safe volume per run, and the location of the control sensor. This data is more important than the pump brand name. If they are not there, automation will work on guesswork.

## Timer, sensor or controller

The timer is suitable when the conditions are stable: identical pots, moderate temperature, understandable water flow. It turns on the pump on a schedule and does not know what is happening in the soil. It's fine for a simple scenario, but doesn't do well with heat, cold snaps, moving plants around, and clogged drip lines. The difference is discussed in more detail in the article [irrigation controller versus timer](/articles/kontroller-poliva-protiv-taymera).

Watering using a humidity sensor is more flexible, but only if the sensor is at the right point. It is not placed close to the drip: there the soil gets wet first, and the system will decide that the entire pot has already been watered. It is not placed against the wall, where the substrate dries out otherwise. Practical installation errors are discussed in the material about [soil moisture sensor for automatic watering](/articles/datchik-vlazhnosti-pochvy-dlya-avtopoliva).

A controller is needed where one condition is not enough. It can take into account the pause between waterings, the pump operation limit, the readings of several sensors, the presence of a leak, and the manual service mode. In GrowerHub, this approach is convenient because the watering rule is associated with the log: you can see not only the current humidity value, but also the history of inclusions.

## Pump, reservoir and tubes

The pump must provide sufficient flow to the furthest point, but not turn a short start into an overflow. For indoor pots, it is often not the power that is more important, but the repeatability of the dose: the same impulse should produce approximately the same volume. This is easy to check: place a measuring cup, turn on the pump for a fixed time and repeat several times.

It is better to calculate the reservoir not in liters “by eye”, but in days of safe autonomy. If a zone typically consumes two liters per week, a one liter capacity will not allow for quiet automation. In this case, a tank that is too large without leakage protection increases the damage caused by an error. Therefore, next to the automatic watering system, a [Zigbee-leakage sensor](/articles/zigbee-datchik-protechki-dlya-avtopoliva) or another independent brake light is appropriate.

Tubes and IVs also affect the result. The long, soft tube can get pinched, the IV can become clogged, and the difference in height changes the flow. Before turning on the automatic mode, it is worth flushing the system with water, checking each point and taking a photo of the location of the lines for future maintenance.

## Safe minimum of automation

A good automatic watering system should not only be able to turn on the pump, but also refuse to water. The minimum set of restrictions: the maximum duration of one launch, a daily water limit, a pause between starts, manual blocking during maintenance and notification if the sensor has not been updated for a long time. Without these rules, even an accurate sensor will not save you from a stuck relay or an empty container.

If the system is cloud dependent, consider what will happen if the internet goes down. For plants, local logic is usually safer: the controller continues to execute the basic rules, but the cloud is needed for viewing, reporting and notifications. This topic is separately covered by the material about [local automation without the cloud](/articles/lokalnaya-avtomatizatsiya-bez-oblaka).

## How to link to GrowerHub

In GrowerHub it is convenient to start with manual mode. Record humidity, watering volume and plant response for several days, then turn on an automatic rule with careful limits. After the first week, look not only at the average values, but also at the shape of the graph: how quickly the humidity drops after watering, how different days differ from each other, are there any sharp dips in the relationship.

If the schedule has become predictable, you can complicate the scenario: add different profiles for seedlings and adult plants, link watering to temperature, set up notifications for leaks and low water levels. But the basic principle remains the same: first understandable area, then measurement, then automation.

## Conclusion

Choose automatic watering as a risk control system. For simple indoor plants, a careful timer and a proven dosage are enough. For boxing, greenhouses and long-term absence, you need a controller with sensors, limits and an emergency stop. The more clearly the watering zone is described before purchasing the equipment, the less likely it is that the automation will turn on beautifully and take poor care of the plants.
