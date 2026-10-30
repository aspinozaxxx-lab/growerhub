---
translation_of: "raskhod-fitolampy-cherez-pomoshchnika"
slug: "check-grow-light-energy-use-with-the-assistant"
title: "How much energy does your grow light use?"
summary: "Ask for a lamp's daily energy use and check whether its history covers the whole period. A demo example with incomplete data."
created_at: "2026-10-30"
updated_at: "2026-10-30"
cluster: "svet-dlya-rasteniy"
product_cta: false
tags: ["grow lights", "energy use", "assistant"]
keywords: ["grow light electricity consumption", "grow light energy history", "energy use assistant"]
related: ["otchety-po-urozhayu-i-resursam", "istoriya-mikroklimata-i-grafiki", "avtomatizatsiya-sveta-v-groubokse"]
hero_image: "/content/articles/illustrations/raskhod-fitolampy-cherez-pomoshchnika.webp"
hero_alt: "Illustration of a plant shelf under white grow lights and an energy-use check"
---

When it is already dark outside, the plant shelf is still glowing. Light catches the leaves, a new shoot reaches upwards, and the little indoor garden carries on with its own routine. Somewhere along the way, a practical question comes to mind: how much electricity does all this light use?

You can ask the GrowerHub assistant about one lamp's energy use. There is one detail to check alongside the number: whether saved readings cover the day you chose.

## One lamp and a clear date

We checked the grow light in the demo's “Tomatoes” zone for a completed day, 24 October. Specifying the start, end and time zone makes it easier to find the same record afterwards.

This is an English translation of the Russian request we tested. Use the zone name displayed in your demo:

> Check the grow light's electricity use in the demo “Tomatoes” zone for 24 October 2026: from 00:00 on 24 October to 00:00 on 25 October, Moscow time (UTC+3). How many kWh do the measurements show? Say whether data covers the whole period and identify any gaps. Do not calculate consumption from the lamp's rated power. Do not change or switch anything on.

For your own lamp, use its name, zone and the date you need. A measured answer requires energy readings from that device in GrowerHub. A lamp name or the watts printed on its box cannot supply those readings.

## A number, with part of the day missing

In our check on 30 October 2026, the assistant showed **about 0.115 kWh in the available measurements for 24 October**. It immediately explained that the data was incomplete. It listed intervals without saved readings, including the start of the day and the time from the last record until midnight.

The reply stopped at its limit after the number and the explanation of gaps were visible. It did not calculate consumption from rated power or change settings.

It is tempting to take that neat little number as the lamp's daily use. However, **the full day's consumption is unknown from this history**. Missing hours do not become hours with zero consumption.

Demo readings are simulated. They help explain the report; your real lamp's consumption needs its own data.

## Find the same record in the history

Return from the conversation to “Overview”, find the zone and select its “Light” tile. The panel has a “By day” list below the chart. In our check, the **24 Oct** row showed the same **0.115 kWh**. The demo's time zone was **Europe/Moscow**.

The list also shows how long the light was on. That answers a separate question: time switched on does not establish an uninterrupted history of energy readings.

Check the lamp, date and day boundaries, then return to the explanation of gaps. Matching numbers in the chat and the daily list helps you check the answer; it cannot fill in missing history. The [guide to reading charts](/en/articles/microclimate-history-and-charts-how-to-read-data-without-panic/) has more examples of interpreting breaks in the records.

## Try one day first

Choose one completed day and one lamp. Ask for energy use together with data coverage, then open the corresponding history row. If readings are missing, wait for a suitable observation period. There is no need to change the lighting schedule just to check the figures.

[Open the assistant on the demo farm](/app/demo/?view=assistant&lang=en) and ask about a date. A new demo will have different history, so choose a past day with visible records. A useful first result is understanding what the kWh figure describes and which questions the data cannot answer yet.
