---
translation_of: "bazovyy-i-avariynyy-rezhimy-ustroystv"
slug: "device-baseline-and-fallback-modes"
title: "What your fan does between cycles — and when readings stop"
summary: "Choose a state between routines and a separate fallback when required readings are missing. GrowerHub shows which source controls the device."
created_at: "2026-11-01"
updated_at: "2026-11-01"
cluster: "zhurnal-i-sovetnik-uhoda"
tags: ["GrowerHub", "routines", "ventilation", "climate"]
keywords: ["fan stays on after routine", "smart socket baseline", "automation fallback mode"]
related: ["prioritety-scenariev-i-vremennoe-upravlenie", "poprobovat-pomoshchnika-na-demoferme", "pomoshchnik-rasteniy-i-scenarii-growerhub"]
hero_image: "/screenshots/assistant/control-policy-en.webp"
hero_alt: "Baseline and fallback settings for a fan in GrowerHub"
---

A three-minute ventilation cycle has finished. Should the fan stop, or keep cooling? Each assigned lighting, fan and air-conditioning channel now has a **baseline state**. It applies when no routine requests another state. The default is OFF.

If climate control still needs cooling, its request takes priority. Finishing a short cycle therefore does not automatically stop a fan in a hot greenhouse.

## Choose the two states

1. Open [Routines](/app/automations/?lang=en) and select a greenhouse.
2. Find the device in **Who is in control now**.
3. Open **Baseline and fallback modes**.
4. Choose the baseline and the state for **When required readings are missing**.
5. Select **Save modes**. The change applies immediately unless a higher-priority request is active.

![Baseline and fallback settings, shown for a fan](/screenshots/assistant/control-policy-en.webp)

For short ventilation cycles, leave the baseline OFF. If you want ventilation to continue when temperature readings stop, choose ON for the fallback separately. Each channel keeps its own settings; both default to OFF.

## When fallback takes over

Fallback applies when an **enabled routine** lacks required input: fresh sensor readings, a socket's required state, or equipment the routine needs. The card explains the cause. A valid false condition, such as the lights being off, is not a missing-reading fault.

Fallback takes priority over automatic routines. Once the inputs recover, GrowerHub uses the current conditions again. Missed cycles are not replayed in a burst.

Temporary manual control takes priority over both modes. You can turn ventilation off for 15 minutes while checking plants. The missing-data warning stays visible; when the manual period ends, GrowerHub selects the applicable mode again.

## A request is different from confirmation

The card separates the actual state, **Requested state** and controlling source. A pending message means a fresh device confirmation is still missing. If confirmation fails, the desired state remains visible instead of appearing to have succeeded.

An offline socket cannot be switched by the server. Following an unconfirmed command, GrowerHub waits for a newer live observation before reconciling again. A matching observation confirms the state without another command.

## Existing routines and restarts

A timed hold releases its request when it ends. A normal routine's ON/OFF command leaves an explicit target until the next command, a revision change or disabling that routine. The baseline therefore cannot immediately undo a scheduled ON command.

A disabled greenhouse requests OFF, while a temporary manual request can still take priority. Settings and active requests are stored on the server; restarting does not restart their timers. After reassigning equipment, review the new assignment's default modes. Pumps keep their own limited watering sessions and do not use these switch settings.

Try the controls on the [demo farm](/app/demo/?view=automations&lang=en). Its devices are virtual, with the same coordination mechanism used for a connected farm.
