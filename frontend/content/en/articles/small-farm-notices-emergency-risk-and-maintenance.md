---
translation_of: uvedomleniya-v-mini-ferme
slug: mini-farm-alerts-emergencies-risks-and-maintenance
title: 'Mini-farm alerts: emergencies, risks and maintenance'
summary: >-
  Which greenhouse alerts are useful, how to choose thresholds, and what you
  can already check in GrowerHub: readings, history and automation.
created_at: '2026-07-23'
updated_at: '2026-09-19'
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

A message such as “check the seedlings: moisture is below your chosen threshold” can be more useful than constantly checking charts. First decide which event needs action and which can stay in the history.

## What GrowerHub supports today

**GrowerHub does not currently send Telegram, email or push notifications.** It also has no ready-made alert profiles with thresholds for individual plant species. The recommendations below describe how to choose useful alerts, rather than how to enable an existing feature.

You can already view sensor readings and history, equipment state and watering records, and configure lighting, irrigation and climate scenarios. [Open Automations in the demo](/app/demo/?lang=en&view=automations) to try them with virtual devices and simulated readings. An automation scenario controls equipment; it does not also send its owner a message.

Keep any important alerts already configured in your current system while trying GrowerHub. Choose one automatic controller per actuator. See [GrowerHub and Home Assistant via MQTT](/articles/growerhub-i-home-assistant-cherez-mqtt) for the connection steps.

## Choose thresholds for your installation

A plant name alone is not enough to choose a moisture threshold: substrate, sensor placement and the sensor's scale matter. Compare readings with the plant and your own observations first. For lighting, distinguish lamp operating time from measured illuminance: an active smart plug does not prove that the plant receives enough light.

Describe one rule in plain language: “if this reading stays beyond the chosen limit, tell me which zone to check.” For non-critical deviations, consider duration and limit repeated messages. Numbers in the examples below illustrate message structure, not plant-care recommendations.

## Emergencies

A leak or a pump that does not stop needs attention at the installation. Decide who will inspect it and how to shut it down safely. A notification cannot replace hardware limits or checking actual equipment state.

An example message is “Greenhouse 2: water detected; inspection needed.” Only add “pump stopped” after the equipment confirms it. Sending a stop command alone is not proof.

## Risks

A risk is a deviation worth checking before it becomes a problem. Temperature might stay above your chosen value, or soil moisture might drop unusually quickly. A useful message identifies the zone, reading, update time and action to take.

For example: “Seedlings: temperature has exceeded your threshold for 30 minutes; check ventilation.” This is an example of a proposed alert, not a record of a GrowerHub notification. Missing fresh readings should also be distinguished from a normal measurement.

## Maintenance

Include batteries, filters, drippers and the reservoir in your regular inspection. Give reminders a convenient time and an owner so that they do not get lost among urgent events.

GrowerHub does not currently schedule these reminders. You can record completed work and observations in the plant journal.

## Information events

An ordinary completed watering session does not always need a message. In GrowerHub, check its result in the watering log. Keep plant observations separately so you can compare care with the plant's condition later.

## Start with one useful event

Choose one zone, one measurable value and one action when it changes. Check that readings are fresh and look at their history. If you need GrowerHub alerts, tell us what you grow, which sensor you use and which event you want to notice in time. A concrete example helps define a useful feature without promising unsupported capabilities.
