---
translation_of: "zigbee-klapan-poliva-home-assistant"
slug: "zigbee-irrigation-valve-home-assistant-growerhub"
title: "Zigbee irrigation valve with Home Assistant and GrowerHub"
summary: "How to choose and pair a Zigbee irrigation valve through Zigbee2MQTT, verify Home Assistant controls, add water safeguards, and use it in GrowerHub."
created_at: "2026-08-16"
updated_at: "2026-08-16"
cluster: "home-assistant-i-diy"
tags:
  - "GrowerHub"
  - "Home Assistant"
  - "Zigbee"
  - "irrigation"
keywords:
  - "Zigbee irrigation valve Home Assistant"
  - "Zigbee2MQTT irrigation valve"
  - "Home Assistant watering valve"
related:
  - "home-assistant-dlya-rasteniy"
  - "zigbee-rozetka-dlya-sveta-i-nasosa"
  - "bezopasnyy-avtopoliv-limity-i-avariynyy-stop"
  - "zigbee-datchik-protechki-dlya-avtopoliva"
---

A Zigbee valve can open an irrigation line without a separate smart plug and mains relay. However, a Zigbee label does not prove that a particular hardware revision is supported by Zigbee2MQTT, reports its position, or closes safely after a power failure. Verify both the digital capabilities and the physical water flow before automating it.

The data path is straightforward: the valve joins Zigbee2MQTT and then appears in Home Assistant and GrowerHub through MQTT. Keep control in one system and use the other for state or history so that two independent rules do not fight over one actuator.

## Choosing a suitable valve

Find the exact model in the [Zigbee2MQTT supported-device catalog](https://www.zigbee2mqtt.io/supported-devices/) before buying. Read the converter's actual `exposes`, not just the product name.

| Check | Why it matters |
|---|---|
| a writable `state`, `open`, `position`, or equivalent property exists | without a command, the device can only be observed |
| the device reports its state rather than assuming success | a sent command does not prove that the valve moved |
| its behavior after power returns is documented or tested | some devices restore the previous position |
| thread, diameter, flow direction, and working pressure fit the line | software compatibility cannot correct plumbing errors |
| a manual shutoff is available | water can be stopped without Zigbee, internet, or an app |

Identical white-label enclosures may contain different firmware. “Works with Tuya” in a shop listing does not guarantee the required Zigbee2MQTT commands; the model ID reported after pairing is what matters.

## Step 1: Pair the valve with Zigbee2MQTT

1. Install the valve without water or on a short, safe test loop.
2. Enable permit join for a limited time.
3. Factory-reset the exact model according to its instructions.
4. Perform the first pairing close to the coordinator.
5. In Zigbee2MQTT, verify `model`, `manufacturer`, `exposes`, `availability`, and the last message time.

If the device will not join or disappears after a power interruption, do not remove it immediately. Follow the [step-by-step Zigbee diagnostics](/articles/pairing-zigbee-pochemu-ustroystvo-ne-nahoditsya/) and determine whether the network still sees the existing entity.

## Step 2: Verify the Home Assistant entity

Zigbee2MQTT discovery may expose the device as a `valve` or a `switch`, depending on its converter. Home Assistant defines valve states such as `open`, `opening`, `closed`, `closing`, `stopped`, `unavailable`, and `unknown`, but a specific device may support only a subset. Compare the UI with the [official Valve entity documentation](https://www.home-assistant.io/integrations/valve) and the actual `exposes`.

Before creating an automation, run several short manual tests:

- an open command physically allows water to flow;
- a close command fully stops the flow;
- the interface changes after device confirmation;
- `unavailable` reflects a real loss of communication;
- behavior after power loss and recovery is predictable.

If Home Assistant exposes only a `switch`, that is not inherently worse. Use the entity the converter provides and do not assume intermediate valve positions.

## Step 3: Add irrigation safeguards

A moisture threshold alone is not enough. Each opening needs:

1. a maximum duration;
2. a minimum recovery interval;
3. a daily time or measured-volume limit;
4. a block on stale data and `unavailable` devices;
5. immediate closure after a leak alarm;
6. a manual maintenance lockout.

A software timer does not replace a physical water shutoff. An unattended line benefits from a normally closed actuator, a separate master valve, and independent confirmation of actual flow.

## Step 4: Choose one control system

There are two clear arrangements:

- Home Assistant runs the local automation while GrowerHub receives state, history, and zone context;
- GrowerHub controls a verified writable capability through Zigbee2MQTT while Home Assistant remains a local interface and integration layer.

Do not leave two schedules active for one valve. Disable the old rule before moving control, perform a manual test, and then run one supervised automatic cycle.

## How the valve appears in GrowerHub

GrowerHub reads the Zigbee2MQTT `exposes` list. Every discovered model can show its available metrics, but a command is accepted only for a writable property. An unknown capability remains read-only until its behavior is understood.

After connection:

1. create a zone;
2. assign the valve as an actuator only when a command is available;
3. add a moisture sensor or use a schedule;
4. configure protective limits;
5. test manual closure before a short supervised cycle.

## When a valve is preferable to a pump

A valve is useful when the line already has stable pressure and separate branches need to open. A pump is needed when water comes from a reservoir or the system must create pressure. Some installations use both a shared pump and zone valves. In that layout, the wrong sequence can leave the pump with no flow, so opening and shutdown must be designed as one scenario.

## Checklist before unattended operation

- the exact model ID and writable command are confirmed;
- flow direction, pressure, and power match the device specification;
- the valve has opened and fully closed several times with water;
- Zigbee loss and power recovery have been tested;
- per-run and daily limits are active;
- leak detection blocks new openings;
- water can be shut off manually;
- only one system decides when to open the valve.

Start with short supervised tests. GrowerHub can then keep the valve, sensors, and zone history in one dashboard, with connection help available in Telegram.
