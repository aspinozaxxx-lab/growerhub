---
translation_of: zigbee-datchik-protechki-dlya-avtopoliva
slug: zigbee-leakage-sensor-how-to-protect-automatic-watering
title: 'Zigbee leakage sensor: how to protect automatic watering'
summary: >-
  Where to install the Zigbee leakage sensor, how to connect it with the
  emergency stop of the pump and why it is a basic element of safe automatic
  watering.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - Zigbee
  - leak
keywords:
  - Zigbee leakage sensor
  - automatic watering protection
  - safe automatic watering
related:
  - bezopasnyy-avtopoliv-limity-i-avariynyy-stop
  - zigbee-dlya-teplitsy-kakie-ustroystva-polezny
  - zigbee-rozetka-dlya-sveta-i-nasosa
  - uvedomleniya-v-mini-ferme
hero_image: /content/articles/illustrations/zigbee-datchik-protechki-dlya-avtopoliva.webp
hero_alt: 'Illustration GrowerHub: Zigbee leakage sensor next to the tank and pump'
---
![Illustration GrowerHub: Zigbee-leakage sensor next to the tank and pump](/content/articles/illustrations/zigbee-datchik-protechki-dlya-avtopoliva.webp)

The leakage sensor is one of the most underrated elements of automatic watering. It doesn’t help determine when the plant needs water, but it does protect against situations where the water goes the wrong way. The tube jumped out of the pot, the connection leaked, the reservoir cracked, the pump was turned on manually and they forgot to turn it off. In such cases, it is not the accuracy of soil moisture that is important, but a quick stop.

The Zigbee sensor is convenient because it usually runs on a battery and is easily placed near the risk area. But you can’t throw it anywhere. It should be located where water will actually appear in an accident, and at the same time remain accessible for battery inspection and testing.

## Where to put the sensor

The first place is under the reservoir or next to the pump. There are connections here that may leak. The second is under the tray of a group of plants, if the water can come out over the edge. The third is at the distributor or valve. If the automatic watering system is on a rack, check where the water will flow down and place the sensor on this path.

Do not place the sensor in the pot itself or in wet substrate. It will not trigger for an emergency, but for normal watering. Also, do not close it so that water bypasses the contacts. After installation, do a safe test: drop water nearby and make sure that the event is visible in GrowerHub.

## How GrowerHub should react

The leak should block the pump. Just notification is not enough: if a person is sleeping or has left, the water will continue to flow. The rule should turn off the controlled outlet or relay, prohibit new automatic watering, and log the event. It is best to return to normal mode manually after inspection.

If the pump is powered via a Zigbee socket, consider network delays and reliability. For critical scenarios, the emergency stop should be as local and understandable as possible. The limitations of sockets are discussed in the article [Zigbee-socket for light and pump](/articles/zigbee-rozetka-dlya-sveta-i-nasosa).

## Inspection and maintenance

The leak sensor is easy to forget because it is supposed to be silent. Once a month, check the battery, availability and actual operation. After a real leak, dry the contacts, check the housing and make sure the event is reset. If the sensor loses communication, GrowerHub should warn about this before the accident.

For a mini-farm with several zones, notifications should be divided by priority: leakage and pump stop - accident, low battery - maintenance. This approach is described in the article [mini-farm notifications](/articles/uvedomleniya-v-mini-ferme).

## Conclusion

The Zigbee leakage sensor does not replace automatic watering limits, but it covers another risk: the physical exit of water from the system. Place it in the path of possible water, connect it to the emergency stop, check the battery and test the operation. For GrowerHub, this is a basic element of safe watering, and not an option “for later”.
