---
translation_of: nadezhnost-avtomatizatsii-bez-interneta
slug: reliability-of-automation-without-the-internet-what-should-work-locally
title: 'Reliability of automation without the Internet: what should work locally'
summary: >-
  Which greenhouse and automatic watering scenarios should continue to work
  without the Internet, and which can be left for the cloud and remote access.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: mini-ferma-i-neskolko-boksov
tags:
  - GrowerHub
  - reliability
  - local automation
keywords:
  - local plant automation
  - automation without the Internet
  - smart greenhouse for business
related:
  - lokalnyy-kontroller-ili-oblachnyy-servis
  - lokalnaya-avtomatizatsiya-bez-oblaka
  - bezopasnyy-avtopoliv-limity-i-avariynyy-stop
  - uvedomleniya-v-mini-ferme
hero_image: /content/articles/illustrations/nadezhnost-avtomatizatsii-bez-interneta.webp
hero_alt: >-
  Illustration GrowerHub: local controller continues watering and emergency stop
  without internet
---
![Illustration GrowerHub: the local controller continues watering and emergency stop without the Internet](/content/articles/illustrations/nadezhnost-avtomatizatsii-bez-interneta.webp)

The Internet can disappear at the most inopportune moment. For a greenhouse and automatic watering, this should not mean a loss of basic safety. If the pump is already running, you need to be able to stop it. If the leakage sensor is triggered, watering should be blocked. If the light schedule has arrived, the local controller must know what to do.

Reliability without the Internet does not mean abandoning the cloud. The cloud is convenient for remote access, notifications and reports. But critical decisions must have a local basis. Otherwise, the system looks smart only as long as the connection is stable.

## What should work locally

Minimum: pump operation limit, emergency stop for leakage, pause between waterings, light schedule, basic ventilation, service mode and saving the latest events. If the sensor is not updated, the local logic should go to a safe state rather than continue watering with the old data.

Stop is especially important for automatic watering. If the rule starts the pump locally, the stop must also be local. You can't count on the shutdown command to come from the cloud.

## What can the cloud expect?

The cloud can send push notifications, build beautiful reports, show graphs on your phone, sync roles, and store long history. If the connection is lost for an hour, these functions can catch up later. Plants will survive a delayed report, but will not always survive a stuck pump.

After the connection is restored, the system should show what happened offline: what rules worked, whether there were accidents, what data was missing. This is important for the credibility of GrowerHub.

## Data and freshness

Working without the Internet does not help if the local sensors themselves have disappeared. It is necessary to distinguish between two failures: there is no external communication and there is no data from the device. If there is no Internet, the local controller can work. In the absence of fresh data from a critical sensor, he must be careful.

The article [safe automatic watering](/articles/bezopasnyy-avtopoliv-limity-i-avariynyy-stop) describes restrictions that protect against such situations.

## Failure check

Reliability needs to be tested. Disconnect the internet and check if the basic schedule continues. Simulate a leak in a safe place. See if the events have been recorded. Then reconnect and check synchronization. It is better to carry out such a test before the holiday, and not during the accident.

Notifications when communication is restored are also useful: “the system was offline, there were no critical accidents” or “a stop was triggered during the offline mode.” Setting priorities is described in the article [notifications in a mini-farm](/articles/uvedomleniya-v-mini-ferme).

## Conclusion

Security and basic controls should work without the Internet: pump stop, limits, service mode, light and ventilation. The cloud can be responsible for convenience, reporting and remote access. This division of responsibility makes GrowerHub reliable precisely when the connection is unstable.
