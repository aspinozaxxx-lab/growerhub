---
translation_of: uvedomleniya-v-mini-ferme
slug: mini-farm-alerts-emergencies-risks-and-maintenance
title: 'Mini-farm alerts: emergencies, risks and maintenance'
summary: >-
  How to configure GrowerHub alerts so emergencies remain visible without
  turning routine warnings into noise.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: mini-ferma-i-neskolko-boksov
tags:
  - GrowerHub
  - notifications
  - small farm
keywords:
  - notices in the greenhouse
  - smart greenhouse for business
  - greenhouse remote control
related:
  - monitoring-neskolkih-boksov
  - zigbee-datchik-protechki-dlya-avtopoliva
  - roli-polzovateley-v-umnoy-teplitse
  - servisnyy-rezhim-zamena-datchika
hero_image: /content/articles/illustrations/uvedomleniya-v-mini-ferme.webp
hero_alt: >-
  Illustration GrowerHub: mini-farm notifications with emergency, risk and
  maintenance levels
---
![Illustration GrowerHub: mini-farm notifications with emergency, risk and maintenance levels](/content/articles/illustrations/uvedomleniya-v-mini-ferme.webp)

Notifications only help when they are few and clear. If GrowerHub sends a message for every small jump in humidity, people quickly stop responding. If an emergency comes into the general flow without priority, it can be skipped. Therefore, a mini-farm requires levels: accident, risk, maintenance and information event.

Separation is especially important when there are several zones. The owner must understand from the notification: where the problem is, how urgent it is, what the system has already done and who should respond. The phrase “sensor triggered” is weak. The message "Box 2: leakage, pump stopped, inspection required" is already actionable.

## Accidents

Accidents require immediate response or automatic stop. Leakage, overheating for longer than a specified time, frozen pump, absence of a critical sensor, fire or electrical alarm, if such sensors exist. For alarms, the notification must be sent to the responsible channel and remain active until acknowledged.

In GrowerHub the alarm should show the last action of the system. For example, the pump is turned off, watering is blocked, the ventilation relay is turned on. If there was no action, this also needs to be shown. The leakage sensor in automatic watering is described in the article [Zigbee-leakage sensor](/articles/zigbee-datchik-protechki-dlya-avtopoliva).

## Risks

A risk is a situation that could become a problem if it were repeated or prolonged. For example, the temperature is higher than usual for 30 minutes, soil moisture drops faster than the weekly average, nighttime air humidity is high, the sensor battery is low. The risk should not wake everyone up at night, but should be included in the daytime view.

Delay is useful for risks. If the temperature went above the threshold for two minutes and returned, notification may be unnecessary. If the deviation lasts longer or repeats for three days in a row, this is already a signal.

## Service

Maintenance is not an accident, but without it, accidents will become more likely. Low battery, no leak test, need to wash the filter, replace the sensor, check the droppers, clean the ventilation. It is better to send such notifications during working hours and associate them with a task.

A person in charge is required for maintenance. If everyone sees the notification, often no one does. The article [user roles in a smart greenhouse](/articles/roli-polzovateley-v-umnoy-teplitse) describes how to separate access and responsibilities.

## Information events

Not all events require a reaction. Automatic watering is completed, the light schedule is changed, the service mode is turned on, the weekly report is ready. Such entries may be in a log or digest, but should not interrupt accidents.

## Conclusion

Mini-farm notifications should help you take action. Divide them into accidents, risks, maintenance and information events. For each message, indicate the zone, reason, system action, and person responsible. Then GrowerHub reduces noise and increases the chance that an important issue will be handled on time.
