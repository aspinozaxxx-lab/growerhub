---
translation_of: servisnyy-rezhim-zamena-datchika
slug: service-mode-how-to-replace-a-sensor-without-chaos-in-automation
title: 'Service mode: how to replace a sensor without chaos in automation'
summary: >-
  Why do you need a service mode when replacing a sensor, cleaning droppers or
  servicing a zone, and how to prevent the automation from mistaking the service
  for an emergency.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: mini-ferma-i-neskolko-boksov
tags:
  - GrowerHub
  - service
  - sensors
keywords:
  - service mode
  - sensor replacement
  - greenhouse remote control
related:
  - roli-polzovateley-v-umnoy-teplitse
  - sovmestimost-zigbee2mqtt-exposes-availability
  - uvedomleniya-v-mini-ferme
  - datchik-vlazhnosti-pochvy-dlya-avtopoliva
hero_image: /content/articles/illustrations/servisnyy-rezhim-zamena-datchika.webp
hero_alt: >-
  Illustration GrowerHub: service mode when replacing the sensor in the plant
  zone
---
![Illustration GrowerHub: service mode when replacing the sensor in the plant zone](/content/articles/illustrations/servisnyy-rezhim-zamena-datchika.webp)

Service mode is needed so that automation does not confuse maintenance with a real problem. You've removed the sensor from the soil, flushed the dripper, replaced the battery, moved the outlet, or checked for a leak. For the system, this may look like dry soil, loss of communication, an accident, or manual intervention. Without service mode, rules may turn on the pump, send unnecessary alarms, or record incorrect data.

In GrowerHub, it is better to enable service mode for a specific zone or device, and not for the entire farm. Then servicing one sensor does not disable the security of other zones.

## What service mode should do

Minimum: stop automatic actions for the selected zone, mark the reason, show the active status, limit the mode time and record the event in the log. If the service mode is turned on without restrictions, it is easy to forget it, and the zone will remain without automatic care.

This is especially important for automatic watering. When replacing a moisture sensor, the system should not treat missing data as dry soil. When testing a leak, the system may see an alarm event, but it must be marked as a test if the operator triggered it manually.

## Replacing the sensor

Before replacing the sensor, turn on the service mode of the zone, write down the reason and time. Then disconnect the old device, install a new one, check pairing or connection, make sure that the data is updated. After this, compare the values with the expected state of the substrate and only then return the automatic rule.

If Zigbee2MQTT is used, check exposes, availability and device name. The new model may render fields differently. The article [compatibility Zigbee2MQTT](/articles/sovmestimost-zigbee2mqtt-exposes-availability) explains what to watch.

## Irrigation maintenance

When cleaning the filter, droppers or pump, the service mode should prohibit automatic activation. After servicing, do a hand test, measure the flow and check the pans. If your consumption has changed, update your limit settings or at least log it.

For a humidity sensor, after replacement, the old thresholds may become incorrect. Use it in observation mode for a few days, as described in the article [soil moisture sensor](/articles/datchik-vlazhnosti-pochvy-dlya-avtopoliva).

## Who can include

In a mini-farm, the service mode should be available to the operator, but critical settings after maintenance can be confirmed by the owner or administrator. The log should show who turned the mode on, who turned it off, and what was done. This is due to user roles: [smart greenhouse roles](/articles/roli-polzovateley-v-umnoy-teplitse).

## Conclusion

Service mode protects automation from false outputs during maintenance. It must be limited by zone and time, visible in the interface and recorded in the log. After replacing the sensor or cleaning the irrigation, return to automatic mode only after checking the data and performing a manual test.
