---
translation_of: "prioritety-scenariev-i-vremennoe-upravlenie"
slug: "routine-priorities-and-temporary-control"
title: "Timed ventilation and temperature control: let two routines work together"
summary: "Required ventilation, normal climate control and temporary manual control can now work together."
created_at: "2026-10-22"
updated_at: "2026-10-22"
cluster: "zhurnal-i-sovetnik-uhoda"
tags: ["GrowerHub", "ventilation", "climate", "routines", "assistant"]
keywords: ["greenhouse ventilation control", "automation priorities", "ventilation while lights are on"]
related: ["svoi-scenarii-poliva-cherez-pomoshchnika", "poprobovat-pomoshchnika-na-demoferme", "pomoshchnik-rasteniy-i-scenarii-growerhub"]
hero_image: "/screenshots/assistant/device-priorities-en.webp"
hero_alt: "GrowerHub shows which routine controls the fan and when its hold ends"
---

Your fan turns on when the greenhouse gets hot. You would also like three minutes of ventilation every half hour while the lights are on. Two independent routines could compete: one turns the socket on, the other immediately turns it off.

GrowerHub coordinates these requests. Required ventilation gets temporary priority. Between cycles, climate control manages the fan again.

## Three minutes on does not mean 27 minutes off

We want **at least three minutes of ventilation every half hour while the lights are on**. If the greenhouse is still hot afterwards, climate control can leave the fan running. If it is cool, it can turn the fan off.

“Run for a minimum of three minutes” and “run for three minutes, then always turn off” are different requests. We want the first. When its hold ends, GrowerHub withdraws that request and evaluates what the other routines currently need.

## Ask the assistant

[Open the assistant on the demo farm](/app/demo/?view=assistant&lang=en) and try:

> Find a greenhouse with lights and an exhaust fan. Prepare a routine: while the lights are actually on, the fan runs for at least three minutes every 30 minutes, with priority above climate control. Start the first cycle immediately. When the lights turn off, release the hold and let climate control take over. Save it disabled and show the preview.

Nothing changes before you confirm the proposal. Review the greenhouse, duration, interval, first cycle and behaviour when the lights turn off. Prepare a separate routine for each greenhouse.

Once saved, open **Routines → My routines** at the bottom of the page. Details show the conditions and preview. Enable it with its switch after reviewing it.

## See why a socket is on

Select a greenhouse on the **Routines** page. **Who is in control now** shows the controlling routine, its reason and the end of temporary control. Expand the other requests to see what else needs the device.

![Current controller and temporary manual control](/screenshots/assistant/device-priorities-en.webp)

A sent command is not a confirmed state. Until the socket reports the requested state, you see **Waiting for the device to confirm**. The three-minute hold starts after confirmation. If confirmation does not arrive in time, you see an error.

## Take over temporarily

Choose **Change temporarily**, select on or off, enter the number of minutes and confirm. For example, turn the fan off for ten minutes while working with your plants.

Use **End manual control** to release it early. When it expires, GrowerHub also evaluates the current routine conditions instead of blindly restoring an old socket state.

## Connection interruptions and limits

The condition uses the actual light state and checks how fresh that reading is. You can choose to release immediately when the light turns off or finish the current cycle. Stale data always ends the hold.

A server restart does not restart an already confirmed three-minute hold. Missed cycles are not queued for catch-up. At equal priority the earlier request keeps precedence, preventing repeated switching between equal requests.

Priorities cover lighting, exhaust fans and cooling. Watering keeps its separate duration and dose limits, and equipment protections remain independent of priority. Existing routines do not need to be reconfigured.
