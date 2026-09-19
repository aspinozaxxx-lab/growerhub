---
translation_of: monitoring-playbook
slug: greenhouse-monitoring-quick-playbook
title: 'Greenhouse monitoring: quick playbook'
summary: >-
  Start monitoring with GrowerHub: one greenhouse, fresh readings, sensor
  history, daily checks and one verified automation scenario.
created_at: '2026-07-23'
updated_at: '2026-09-19'
cluster: mini-ferma-i-neskolko-boksov
tags:
  - GrowerHub
  - monitoring
  - greenhouse
keywords:
  - greenhouse monitoring
  - greenhouse microclimate control
  - greenhouse remote control
related:
  - avtomatizatsiya-teplitsy-chto-kontrolirovat
  - monitoring-neskolkih-boksov
  - uvedomleniya-v-mini-ferme
  - sensor-checklist
hero_image: /content/articles/illustrations/monitoring-playbook.webp
hero_alt: 'Illustration GrowerHub: greenhouse monitoring playbook with zones and sensors'
---
![Illustration GrowerHub: greenhouse monitoring playbook with zones and sensors](/content/articles/illustrations/monitoring-playbook.webp)

The first useful result is one real sensor in a clearly named zone, fresh readings and a chart you can easily open. Start there, then add one useful automation. This makes it easier to assess GrowerHub and spot setup problems.

Explore the interface in the [demo without signing up](/app/demo/?lang=en). Its devices and readings are virtual; connect physical equipment separately in your account.

## Step 1: separate zones

In Settings → Zones, create a farm and one greenhouse with a clear name, such as “Seedling rack”. Assign its equipment and plants in Farm constructor. You do not need to configure every growing area at once.

For multiple boxes, use the same card structure. This is discussed in more detail in the article [monitoring multiple boxes](/articles/monitoring-neskolkih-boksov).

## Step 2: Connect a minimum of sensors

Choose an existing compatible sensor and a reading you actually use to make decisions. If Zigbee2MQTT is already running, follow the [MQTT connection guide](/articles/growerhub-i-home-assistant-cherez-mqtt). Your Zigbee devices do not need to be paired again.

Compare the value and update time with your existing system, then open its chart from Overview. GrowerHub history starts after connection; another system's old history is not imported automatically.

A checklist of basic sensors is in the article [checklist of sensors for a greenhouse](/articles/sensor-checklist).

## Step 3: decide what to do when a reading changes

Write down what to check when temperature or moisture is unusual, or fresh readings stop arriving. **GrowerHub does not currently send Telegram, email or push notifications or provide alert thresholds by plant species.** Keep important existing alerts in your current system. The [mini-farm alerts article](/articles/uvedomleniya-v-mini-ferme) explains how to choose useful events.

If you already have a compatible actuator, configure one lighting or climate scenario in Automations. Choose a single source of automatic commands for it. After the scenario runs, check actual equipment state rather than just command delivery. Check support for your particular pump and irrigation setup separately.

## Step 4: Daily inspection

Check fresh readings, plants and equipment every day. If you use irrigation, inspect the reservoir and trays and open the watering log. You can save observations in the plant journal. Leave settings alone when everything is working as intended.

## Step 5: weekly analysis

After a week, compare charts and notes: which deviations did you notice, which task became easier, and are readings still arriving? Only assess measurements your equipment actually provides; missing data does not mean zero. Choose one change for the next week.

## Conclusion

Start with one greenhouse and live readings, then verify one useful automation. This lets you evaluate GrowerHub on your own installation while keeping the link between settings and actual results clear.
