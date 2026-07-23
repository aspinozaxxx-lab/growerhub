---
translation_of: oshibki-avtopoliva-kotorye-ubivayut-rasteniya
slug: 7-automatic-watering-mistakes-that-cause-plants-to-become-overwatered-or-dry
title: 7 automatic watering mistakes that cause plants to become overwatered or dry
summary: >-
  How to avoid dangerous automatic watering errors: wrong zone, wrong sensor,
  missing limits, leaks, old data and starting without testing.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: avtopoliv-i-kontroller-vyrashchivaniya
tags:
  - GrowerHub
  - errors
  - automatic watering
keywords:
  - auto watering errors
  - overflow of plants
  - auto watering doesn't work
related:
  - avtopoliv-dlya-rasteniy-kak-vybrat
  - bezopasnyy-avtopoliv-limity-i-avariynyy-stop
  - datchik-vlazhnosti-pochvy-dlya-avtopoliva
  - pereliv-ili-nedoliv-po-dannym-datchika
hero_image: >-
  /content/articles/illustrations/oshibki-avtopoliva-kotorye-ubivayut-rasteniya.webp
hero_alt: 'Automatic watering errors: overflow, dry zone and emergency stop'
---
![Automatic watering errors: overflow, dry zone and emergency stop](/content/articles/illustrations/oshibki-avtopoliva-kotorye-ubivayut-rasteniya.webp)

Dangerous automatic watering usually appears to be working properly: the relay clicks, the pump makes noise, and there are percentages on the graph. The problem is discovered later - one pot is flooded, the water has not reached the other, or the old reading has been allowing new cycles for hours. Below are seven errors to check before launching without supervision.

## Brief table

| Error | Risk | How to check |
|---|---|---|
| different plants in one zone | some get too much or too little water | compare pots, substrate, drippers and flow |
| sensor at the dropper | locally wet, root zone dry | manual watering and checking at several points |
| no time limit | overflow when the sensor or relay is stuck | forcefully simulate lack of reaction |
| old value is considered normal | launch based on data that no longer describes the soil | disconnect the sensor and check the blocking |
| no leak control | water continues to flow when the tube breaks | physically wet the leakage sensor |
| command equals result | the interface says “on”, but no water flows | measure flow or feedback |
| launch before departure | the first hidden error remains without a person | carry out several cycles under supervision |

## Error 1. One mode for different plants

The same pump time does not mean the same water. Pots, tube heights, drippers, substrate and crop consumption vary. Divide the system into zones with similar conditions and measure the actual flow on each line.

If the zone is heterogeneous, it is better to temporarily leave some of the plants with manual watering. Automation does not correct incorrect groupings.

## Error 2. The sensor measures the drip, not the roots

With a dripper, the sensor quickly gets wet and stops watering early. In a dry region, the opposite happens: the pump starts again, although the bulk of the substrate is still wet. Attach the sensor to a representative point and compare the reading with the manual test and the weight of the pot for several cycles.

Don’t tolerate someone else’s “35%” threshold. The percentage is a specific calibration scale. The practice is discussed in the article [how to select and install a soil moisture sensor](/articles/datchik-vlazhnosti-pochvy-dlya-avtopoliva/).

## Error 3. There is a threshold, but no limits

The “below threshold - enable” rule requires at least four restrictions:

1. maximum time for one launch;
2. minimum pause after watering;
3. daily water or time limit;
4. prohibition of parallel launch of the same zone.

Stopping should work even if the interface is restarted or the connection is lost. A software timer is useful, but serious loads require independent controller protection and a safe relay state.

## Error 4. Old data looks like fresh data

The last value may remain on the panel after the sensor is lost. Add update time and availability status. If the data age is exceeded, the new action is blocked rather than performed “last known”.

In Home Assistant, the trigger, conditions and actions are separate parts of the automation; this is useful for explicitly checking availability. The official logic is described in the documentation for [triggers](https://www.home-assistant.io/docs/automation/trigger/) and [conditions](https://www.home-assistant.io/docs/automation/condition/).

## Error 5. Leak only sends notification

The notice does not stop the water. Triggering a leak should turn off the pump, block new starts and record the cause. After the accident has been eliminated, the automatic mode can only be returned manually.

Check the protection physically: wet the sensor during a short test watering. If the result depends on the owner's phone, it is not an emergency stop.

## Error 6. The command is considered a fact

The state of the `ON` relay does not yet prove that the water has reached. The pump may run dry, the tube may fall off, or the dropper may become clogged. For the first version, measure the volume manually and check the lines regularly. For a developed system, a flow meter, pressure or other independent indicator of the result is added.

In the log, separate “command sent”, “equipment confirmed status” and “irrigation is actually completed”. This way, diagnostics don't become guesswork.

## Error 7. The first automatic cycle is started before departure

The new system must go through several supervised cycles. After any change in the sensor, tube, threshold or firmware, the test countdown starts anew. Before autonomous operation, test separately:

- sensor loss;
- network shutdown;
- power return;
- leakage response;
- manual locking;
- achieving maximum time.

## What the working screen should look like

On one screen you need the zone, sensor freshness, watering status, last start and reason for blocking. The graph follows and helps to understand the result. An example of such a division is on the [mini-farm automation](/avtomatizatsiya-mini-fermy/#demo-ekrany) page.

## Pre-launch checklist

- plants are correctly divided into zones;
- the flow rate of each line is measured;
- the sensor is calibrated and secured;
- old data blocks the launch;
- time, pause and daily limit are configured;
- leakage stops the pump;
- the team and the physical result do not mix;
- failure tests have been completed under supervision.

GrowerHub helps bring zones, readings, controls and history into one context, but physical security begins with installation and water testing. The automatic mode is turned on only after each item has been confirmed on real equipment.

For a safe first step, a pump is not needed: connect the [climate sensor](/oborudovanie/datchiki/) and, if necessary, the [Zigbee socket](/oborudovanie/zigbee-rozetki/). Our own [pump GrowerHub](/oborudovanie/nasos-dlya-poliva/) is being prepared for the first users - you can learn about the development and join the tests.
