---
translation_of: kak-sovetnik-prinimaet-reshenie-o-polive
slug: how-the-advisor-makes-watering-decisions
title: How the advisor makes watering decisions
summary: >-
  What data does the care advisor need to make a watering recommendation
  understandable: humidity, trend, history, limits and growth stage.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zhurnal-i-sovetnik-uhoda
tags:
  - GrowerHub
  - advisor
  - watering
keywords:
  - tips for watering plants
  - recommendations for plant care
  - plant care using sensors
related:
  - pereliv-ili-nedoliv-po-dannym-datchika
  - dnevnik-rasteniya-chto-zapisyvat
  - stadii-rosta-i-nastroyki-uhoda
  - bezopasnyy-avtopoliv-limity-i-avariynyy-stop
hero_image: /content/articles/illustrations/kak-sovetnik-prinimaet-reshenie-o-polive.webp
hero_alt: 'Illustration GrowerHub: Advisor analyzes humidity, history and watering limits'
---
![Illustration GrowerHub: Advisor analyzes moisture, history and watering limits](/content/articles/illustrations/kak-sovetnik-prinimaet-reshenie-o-polive.webp)

The watering advisor should explain the decision, and not just issue the command “water.” There are many reasons for a plant to look worse: dry substrate, overwatering, heat, low light, replanting, root damage, or sensor error. Therefore, a good recommendation is built on multiple layers of data and always takes security into account.

In GrowerHub, the advisor is useful when it shows the course of reasoning: what data is recent, what is the humidity trend, when was the last watering, are there any limits, what stage of growth and what is written in the diary. If this data is not available, it is more honest to ask the user to check the plant than to confidently turn on the pump.

## Layer 1: data freshness

The first question is whether the sensor can be trusted right now. If the humidity has not been updated for several hours, the battery is almost empty, or the device has lost connection, the advisor should not consider the value to be current. It is better to turn old data into a notification to check the sensor.

Freshness is more important than percent accuracy. Even a good sensor is useless if the controller makes a decision based on yesterday's value. Therefore, recommendations should take into account availability and the time of the last measurement.

## Layer 2: trend, not just one number

One point of humidity doesn't tell much. If the value is below the threshold but drops sharply after repositioning the sensor, this is not the same as gradual drying out over two days. If the humidity does not rise after watering, the water may not have reached the sensor or the tube may not be working.

The adviser should look at the dynamics: the rate of decline, the reaction after watering, the repeatability of the cycle. This helps differentiate normal drying from an accident. Detailed diagnostics can be found in the article [overfilling or underfilling](/articles/pereliv-ili-nedoliv-po-dannym-datchika).

## Layer 3: action history

The last watering, volume, fertilizing, replanting and changes in light change the interpretation of the data. If the plant was repotted yesterday, it may temporarily look worse without an immediate change in watering. If you have recently fed, the symptoms should be considered more carefully. If a person manually watered at night, automatic start in the morning may be unnecessary.

A diary is needed here. It doesn't replace sensors, but explains graphs. How to keep records without unnecessary burden is described in the article [plant diary](/articles/dnevnik-rasteniya-chto-zapisyvat).

## Layer 4: stage and zone

Seedlings, mature plants, and recovery from transplanting require different expectations. The same humidity threshold may be normal for one stage and risky for another. In addition, the adviser must understand the zone: pot, bed, box, greenhouse, general tank, type of substrate.

If the area is not described, the recommendation should be cautious. It is better to ask the user for clarification than to apply general advice. More details about the stages can be found in the article [stages of growth and care settings](/articles/stadii-rosta-i-nastroyki-uhoda).

## Layer 5: Security

Even if the advisor believes that watering is needed, the controller must check the limits: pause after the last start, maximum duration, daily volume, leakage, service mode. The recommendation is not to bypass emergency stops. This is especially important if the advisor is associated with an automatic action.

## Conclusion

A good advisor doesn't guess. It checks data freshness, trend, history, stage and security and then explains the recommendation. This approach makes GrowerHub an observation assistant, rather than a black box that feeds one number at a time.
