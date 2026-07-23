---
translation_of: datchik-vlazhnosti-pochvy-dlya-avtopoliva
slug: soil-moisture-sensor-for-automatic-watering-how-to-use-without-false-alarms
title: 'Soil moisture sensor for automatic watering: how to use without false alarms'
summary: >-
  Where to install a humidity sensor, why readings can be deceiving and how to
  turn measurements into a safe rule for automatic watering.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: avtopoliv-i-kontroller-vyrashchivaniya
tags:
  - GrowerHub
  - humidity sensor
  - automatic watering
keywords:
  - soil moisture sensor
  - humidity sensor for irrigation
  - watering by humidity sensor
related:
  - avtopoliv-dlya-rasteniy-kak-vybrat
  - poliv-po-datchikam-ili-po-raspisaniyu
  - pereliv-ili-nedoliv-po-dannym-datchika
  - servisnyy-rezhim-zamena-datchika
hero_image: /content/articles/illustrations/datchik-vlazhnosti-pochvy-dlya-avtopoliva.webp
hero_alt: 'Illustration GrowerHub: plant pot, moisture sensor and soil graph'
---
![Illustration GrowerHub: plant pot, moisture sensor and soil graph](/content/articles/illustrations/datchik-vlazhnosti-pochvy-dlya-avtopoliva.webp)

A soil moisture gauge is not useful because it shows the "true moisture" of the entire pot. It shows the status of a small area around the probe. If you understand this limitation, the sensor helps you notice drying out in time, check the operation of the dripper and stop unnecessary watering. If you don’t understand, it turns automatic watering into a lottery: the system reacts to one point, and the plant lives in the volume of the substrate.

Before automation, it is worth using the sensor for several days only for observation. Water the plant manually, see how quickly the readings grow, where the peak is and how many hours it takes for the graph to return to normal levels. This way you will see not only the number, but also the nature of the zone: a light substrate dries out faster, a dense one retains moisture longer, a large pot changes more slowly than a small one.

## Where to put the sensor

The sensor is not placed directly under the IV. At this point, it receives water first and quickly tells the controller that everything is already wet. The rest of the pot may remain dry. It is also undesirable to place the probe near the wall: there the temperature and evaporation differ from the central zone. It is usually best to choose a point between the drip line and the root zone, at a depth where the substrate reflects the condition of the plant and not just the top dry layer.

For a long bed, one sensor rarely describes the whole picture. This can be used as a reference point, but the irrigation lines still need to be checked visually. If one dropper is clogged, the common sensor may not notice it. Therefore, in a greenhouse, it is useful to combine humidity sensors with a watering log and periodic inspection.

## Why the readings float

Indications depend on the type of substrate, salts, temperature, planting density and quality of contact. After transplantation, the sensor may show differently because air voids have appeared around it. After feeding, the value sometimes changes not because of the water, but because of the composition of the solution. Cheap resistive sensors also degrade in the soil faster than capacitive ones.

Therefore, you should not transfer the threshold from someone else’s instructions without checking. For one user, 35 percent may mean comfortable humidity, for another - an already dry pot. It is more practical to find your range: mark the value before manual watering, then the value an hour after watering and select thresholds with a margin. How to distinguish overfilling from underfilling based on the shape of the graph is described in the article [overfilling or underfilling according to sensor data](/articles/pereliv-ili-nedoliv-po-dannym-datchika).

## How to make a watering rule

The most dangerous rule looks like this: “if the humidity is below the threshold, turn on the pump.” In a real system, restrictions are needed. Add a minimum pause between starts, a maximum duration of one start and a daily water limit. If the sensor has not been updated, the controller should not water blindly, but go into safe mode and send a notification.

A good starting scenario: the sensor is below the threshold several times in a row, a specified pause has passed since the last watering, there is no leakage, the tank is not empty, the service mode is turned off. The pump is turned on briefly, then the system waits until the water is distributed over the substrate. Without such a delay, the controller may make several extra starts in a row because the sensor has not yet had time to react.

## Maintenance and replacement

Any sensor must be checked periodically. Inspect the cable, housing, substrate entry point, and graph stability. If the line does not change after watering, the sensor may have lost contact or failed. If the graph becomes noisy, check your connection and power. During replacement, it is better to turn on the service mode so that the automation does not mistake the missing sensor for dry soil. There is a separate article for this procedure [service mode and sensor replacement](/articles/servisnyy-rezhim-zamena-datchika).

## Conclusion

The humidity sensor is an observation tool, not an absolute judge. It helps automatic watering only when installed at a meaningful point, tested on a specific substrate and associated with safe limits. In GrowerHub it is better to use it first for graphs and notifications, and enable automatic launch after the behavior of the zone has become clear.
