---
translation_of: poliv-po-datchikam-ili-po-raspisaniyu
slug: watering-by-sensors-or-by-schedule-which-is-more-reliable
title: 'Watering by sensors or by schedule: which is more reliable?'
summary: >-
  How to choose between scheduled and sensor-based watering, why the best
  scenario often combines both approaches, and what restrictions are needed.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: avtopoliv-i-kontroller-vyrashchivaniya
tags:
  - GrowerHub
  - watering
  - sensors
keywords:
  - watering by humidity sensor
  - Scheduled watering
  - irrigation automation
related:
  - kontroller-poliva-protiv-taymera
  - datchik-vlazhnosti-pochvy-dlya-avtopoliva
  - bezopasnyy-avtopoliv-limity-i-avariynyy-stop
  - pereliv-ili-nedoliv-po-dannym-datchika
hero_image: /content/articles/illustrations/poliv-po-datchikam-ili-po-raspisaniyu.webp
hero_alt: 'Illustration GrowerHub: watering schedule, humidity sensor and controller'
---
![Illustration GrowerHub: watering schedule, humidity sensor and controller](/content/articles/illustrations/poliv-po-datchikam-ili-po-raspisaniyu.webp)

The “sensor versus schedule” debate is often framed too harshly. In real automatic watering, these are not mutually exclusive options, but different sources of solutions. The schedule gives a rhythm: when it is generally permissible to water. The sensor gives feedback: is water needed now? The controller connects these signals and prevents the system from turning on the pump too often or for too long.

If you select only the schedule, the system is simple and predictable, but does not see the ground. If you select only the sensor, the system is flexible, but becomes dependent on the quality of a single measuring point. A reliable scenario usually looks like this: watering is allowed during a certain window, the sensor confirms dryness, and limits limit the amount of water.

## Strengths of the schedule

The schedule works well when the plants and conditions are stable. For example, identical pots of herbs on a rack or seedlings in cassettes, where the water consumption is checked in advance. The schedule is easy to explain and check: the pump turns on for a short time in the morning, then in the evening if necessary. The log shows the actual runs and the owner quickly notices the deviation.

The weak point of the schedule is the weather and microclimate. After a hot day, the soil dries out faster. After a cool week or transplant, there may be too much water. If the schedule does not change, it waters the past situation, not the current one. Therefore, it is better to use the schedule with a safety margin: short impulses, pauses and regular inspection.

## Strengths of the sensor

The sensor helps you avoid watering when the soil is still wet and respond faster when it dries out. This is especially useful in a box, greenhouse or near heating where conditions change throughout the day. But the sensor only measures its own point. If it is standing near the dropper, the system will not be filled enough. If it is in a dry corner, the system may overflow.

Sensor watering requires setting the range specifically for your zone. You cannot take a universal percentage and consider it correct. First, look at the graph after manual watering, mark the values before the next watering, and only then set the threshold. This is discussed in detail in the article [soil moisture sensor for automatic watering](/articles/datchik-vlazhnosti-pochvy-dlya-avtopoliva).

## Combined logic

A practical scenario for GrowerHub can be described as follows: watering is allowed in the morning and evening, the sensor is below the threshold for several measurements in a row, a pause has passed since the last launch, the daily limit has not been exceeded, there are no accidents. This approach protects against two extremes. The schedule will prevent the pump from turning on every ten minutes at night, and the sensor will not allow you to water the wet substrate simply because the time has come.

Combination logic is especially useful with multiple sensors. For example, one sensor monitors a dry control point, and the second is located in a wetter zone. The controller may not look for the “average number”, but check the conditions: if one sensor is suddenly out of order, it is better to send a notification than to immediately change the entire irrigation.

## How to understand that the script is incorrect

Look not only at the current humidity, but also at the shape of the graph. If after watering the value hardly increases, the water does not reach the sensor or the sensor has lost contact. If the humidity drops too quickly, the dose may be low or the substrate may not hold water well. If the schedule becomes staggered with frequent starts, there is not enough pause after watering.

Separately check the appearance of the plants and the weight of the pot. Automation should support surveillance, not eliminate it. The article [overfilling or underfilling according to sensor data](/articles/pereliv-ili-nedoliv-po-dannym-datchika) shows how to compare the graph with real symptoms.

## Conclusion

The schedule is reliable in its simplicity, the sensor is valuable in its feedback, and a good controller is needed for restrictions. For most home and greenhouse scenarios, it is better not to choose one method forever, but to put together a combined rule: watering window, humidity confirmation, pause, limit and emergency stop. Then the system remains understandable and does not water blindly.
