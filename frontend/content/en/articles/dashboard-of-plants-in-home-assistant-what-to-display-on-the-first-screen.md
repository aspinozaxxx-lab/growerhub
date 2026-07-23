---
translation_of: dashboard-rasteniy-v-home-assistant
slug: plant-dashboard-in-home-assistant-what-to-show-first
title: 'Plant dashboard in Home Assistant: what to show first'
summary: >-
  How to build a Home Assistant dashboard for plants: zones, alerts, data
  freshness, charts, watering, light and quick manual actions.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: home-assistant-i-diy
tags:
  - GrowerHub
  - Home Assistant
  - dashboard
keywords:
  - plant dashboard Home Assistant
  - Home Assistant plants
  - plant monitoring
related:
  - home-assistant-dlya-rasteniy
  - monitoring-neskolkih-boksov
  - mqtt-discovery-home-assistant
  - istoriya-mikroklimata-i-grafiki
hero_image: /content/articles/illustrations/dashboard-rasteniy-v-home-assistant.webp
hero_alt: >-
  Illustration GrowerHub: Home Assistant dashboard with plant zones and
  accidents
---
![Illustration GrowerHub: Home Assistant dashboard with plant zones and accidents](/content/articles/illustrations/dashboard-rasteniy-v-home-assistant.webp)

The Plant Dashboard in Home Assistant should help you quickly understand the status of zones. If the first screen is crowded with all the graphs and entities, the important incident is lost. To care for plants, it is better to start with the statuses: normal, risk, accident, maintenance, data freshness and latest actions.

A good dashboard answers the questions: where is the problem, how urgent is it, what has the automation already done, when was the last watering, are the sensors fresh, what is the current microclimate. A detailed analysis can be provided below or on a separate tab.

## First screen

Show zones on the first screen. For each zone: temperature, air humidity, soil moisture or watering status, leakage, pump/light status, last activity and availability of critical sensors. If there are many zones, it is better to have cards with a compact status rather than long schedules.

Accidents should be visually higher than normal values. If the leak sensor is triggered, the user doesn't have to scroll to see it. The same applies to the inaccessible sensor on which automatic watering depends.

## Charts

Graphs are needed for analysis, but they should be grouped according to their meaning: microclimate, soil, watering, light. Don't mix everything on one canvas. It is more useful for plants to see connections: the light turned on, the temperature rose, air humidity dropped, and soil moisture changed after watering.

How to read such connections is described in the article [history of microclimate and graphics](/articles/istoriya-mikroklimata-i-grafiki).

## Manual actions

Manual watering, light or ventilation buttons must be used with care. Don't make a big "turn on the pump" button without confirmation, a limit or a clear timer. It is better to use scripts with a duration limit and show that the action is manual. Critical commands require a log.

If GrowerHub is responsible for watering rules, Home Assistant dashboard can send a manual command through MQTT, but must not bypass GrowerHub limits. Integration is described in the article [GrowerHub and Home Assistant via MQTT](/articles/growerhub-i-home-assistant-cherez-mqtt).

## Service

Add a service unit: batteries, inaccessible devices, service mode, date of last leak check, filter, reservoir. These things are boring, but they prevent accidents. A separate Maintenance tab is useful for multiple zones.

## Conclusion

A plant dashboard should be a working tool, not a showcase of all sensors. First zones and alarms, then schedules, then manual actions and maintenance. Home Assistant is perfect for such a screen if GrowerHub or another system stores care context and history.
