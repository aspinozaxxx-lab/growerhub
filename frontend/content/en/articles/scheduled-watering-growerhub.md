---
translation_of: "poliv-po-raspisaniyu-growerhub"
slug: "scheduled-watering-growerhub"
title: "Scheduled watering: days, time and duration"
summary: "The new schedule does not require a soil sensor. Observe its plan first, then explicitly enable irrigation control when ready."
created_at: "2026-10-02"
updated_at: "2026-10-07"
cluster: "avtopoliv-i-kontroller-vyrashchivaniya"
related: ["prognoz-vysyhaniya-i-plan-poliva-growerhub","demo-klapan-rashodomer-growerhub"]
hero_image: "/screenshots/news/watering-schedule-en.webp"
hero_alt: "GrowerHub watering editor with a schedule and Observe without commands mode"
---

Once you have checked your normal water dose, you can tie irrigation to specific days and a time. GrowerHub now offers **scheduled watering** without a mandatory soil sensor. Trigger conditions and delivery limits stay visible in one editor.

## Set it up

1. In **Farm**, assign an admitted pump or valve to the greenhouse watering slot.
2. In **Routines**, choose a greenhouse under **Greenhouse settings** and find **Watering**. On a phone, choose the greenhouse in the light schedule and open **Watering**.
3. Under **When to water**, choose **On a schedule**, then select weekdays and a time.
4. Set delivery duration, the minimum interval and the daily delivery time limit.
5. Under **Scenario operation**, keep **Observe without commands**, click **Save watering** and enable the scenario to inspect its plan without operating equipment.

Times follow your profile time zone. Screenshot values are demo examples, not crop watering recommendations.

## Observe before control

A newly selected mode starts in observation: it calculates a time and explains constraints without sending commands. After checking the settings, explicitly select **Control watering** and confirm the change for an enabled scenario.

Device admission, availability, greenhouse state, interval and daily limit are checked before execution. Saving a time does not remove these checks.

## Pulses and missed times

A capable executor can split delivery into short runs separated by pauses. Duration means **total water delivery time**, excluding pauses. The server determines capabilities; not every Zigbee valve is admitted for control.

A missed watering is not caught up after an interruption. Clock changes do not create duplicate runs for one scheduled occurrence. Your previous rule stays until you choose a new mode.

Explore the editor and [virtual valve](/articles/demo-klapan-rashodomer-growerhub/) without equipment in the shared demo farm.
