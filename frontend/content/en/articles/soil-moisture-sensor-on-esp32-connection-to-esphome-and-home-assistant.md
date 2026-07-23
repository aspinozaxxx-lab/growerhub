---
translation_of: esp32-datchik-vlazhnosti-home-assistant
slug: soil-moisture-sensor-on-esp32-connection-to-esphome-and-home-assistant
title: 'Soil moisture sensor on ESP32: connection to ESPHome and Home Assistant'
summary: >-
  How to connect a capacitive humidity sensor to the ESP32, calibrate the ADC in
  ESPHome, transfer the data to the Home Assistant and use it safely for
  watering.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: home-assistant-i-diy
tags:
  - GrowerHub
  - ESP32
  - Home Assistant
keywords:
  - humidity sensor ESP32
  - ESP32 soil moisture sensor
  - ESPHome soil moisture
  - Home Assistant plants
related:
  - diy-ili-gotovyy-kontroller-poliva
  - mqtt-discovery-home-assistant
  - datchik-vlazhnosti-pochvy-dlya-avtopoliva
  - bezopasnyy-avtopoliv-limity-i-avariynyy-stop
hero_image: /content/articles/illustrations/esp32-datchik-vlazhnosti-home-assistant.webp
hero_alt: 'ESP32 with capacitive soil moisture sensor, ESPHome and Home Assistant'
---
![ESP32 with capacitive soil moisture sensor, ESPHome and Home Assistant](/content/articles/illustrations/esp32-datchik-vlazhnosti-home-assistant.webp)

The ESP32 and capacitive sensor are suitable for monitoring soil moisture as long as you treat the reading as a **installation specific measurement** rather than a generic percentage. The voltage depends on the board, power supply, sensor, wire length, substrate and probe position. Therefore, a stable signal is first collected, then calibrated, and only after that automatic watering is discussed.

## What you need

| Component | Requirement |
|---|---|
| ESP32 | working board and ADC pin suitable for the selected configuration |
| sensor | preferably capacitive, without exposed corroding electrodes |
| food | stable and compatible with the board; the sensor output does not exceed the permissible input voltage ESP32 |
| body | ESP32 and connections are outside the wet area |
| control | multimeter and manual substrate condition check |

Before connecting, check the diagram of your board and sensor. The color of the wire and the signature from a random product card are not documentation.

## Step 1: Get the raw ADC value

Don't convert the signal to a percentage at first. Print out the voltage or raw normalized value and see how stable it is for a few hours. The official parameters of the component are given in the ESPHome documentation [Analog To Digital Sensor](https://esphome.io/components/sensor/adc/).

The minimum configuration idea looks like this:

```yaml
sensor:
  - platform: adc
    pin: GPIO34
    name: "Seedlings — moisture, raw signal"
    attenuation: auto
    update_interval: 30s
    filters:
      - median:
          window_size: 7
          send_every: 3
```

GPIO and parameters need to be replaced to suit your board. The median filter reduces single spikes, but does not correct a bad power supply or a flooded connector.

## Step 2: Check installation

Immerse the working part of the sensor into the root zone without touching the wall of the pot or placing it directly under the dripper. Electronics and the top of the board must not be exposed to water. Secure the cable: changing the depth and angle changes the reading noticeably.

Do three checks:

1. the value in air and in a dry substrate does not jump chaotically;
2. after manual watering, the signal changes in the expected direction;
3. As it dries, it gradually returns rather than remaining at the same level.

If the reaction occurs only when the wire is moved, the problem is electrical, not agronomic.

## Step 3: Collect calibration points

Don't use a glass of water as your only "100% point." The sensor behaves differently in water and in the working substrate. It is more practical to write down several real states:

| State | What to write down |
|---|---|
| before regular hand watering | raw value and condition of the plant |
| 30–60 minutes after watering | value after water distribution |
| in a day | rate of change |
| before the next watering | dry point repeatability |

After a few loops, you can apply the filter `calibrate_linear` and limit the result to the range 0–100. The syntax and operation of filters are described in the official ESPHome section [Sensor Component](https://esphome.io/components/sensor/index.html#calibrate-linear). The value pairs must be your own and not copied from the example.

```yaml
    filters:
      - median:
          window_size: 7
          send_every: 3
      - calibrate_linear:
          method: exact
          datapoints:
            - 0.82 -> 0.0
            - 0.39 -> 100.0
      - clamp:
          min_value: 0
          max_value: 100
    unit_of_measurement: "%"
```

The numbers above only show the format. For some sensors the direction will be reversed.

## Step 4. Transfer data to Home Assistant

ESPHome can use native API or MQTT. Native API is simpler if data is needed only Home Assistant. MQTT is useful when the same measurement is read by GrowerHub or another local system. In any case, the entity should become unavailable when the controller is lost, and the interface should show the last update time.

For MQTT, configure state and availability, and for automation, do not use the saved value after the allowed time has expired. General integration rules are described on the page Home Assistant [MQTT](https://www.home-assistant.io/integrations/mqtt/).

## Step 5. First just observation

Leave the sensor for 5-7 days without automatic control. Compare the schedule with manual watering, the weight of the pot and the condition of the substrate. If the threshold has to be changed every day, the problem has not yet been measured.

The dashboard contains useful information about the current value, freshness, schedule and watering marks. An example of a history view is shown on the [mini-farm automation](/avtomatizatsiya-mini-fermy/#demo-ekrany) page.

## Is it possible to connect the pump to the same ESP32

Technically yes, but measurement and safe control are different tasks. Relay, pump and water require suitable power supply, load protection, housing, manual shutdown and independent maximum operating time. Restarting the ESP32 or Home Assistant should not leave the pump running.

If these conditions are not met, leave the ESP32 as the sensor. For future watering, first add a leak, measure flow and check [safe auto watering limits](/articles/bezopasnyy-avtopoliv-limity-i-avariynyy-stop/).

## Result checklist

- ESP32 input does not receive unacceptable voltage;
- the controller and connections are protected from water;
- raw values are stable;
- the sensor is fixed in the working substrate;
- calibration was done over several real cycles;
- if ESP32 is lost, the entity becomes inaccessible;
- the automatic pump does not depend on one program timer.

The percentage then becomes a useful zone-specific scale rather than a random number with a nice blob icon.

ESP32 with its own local MQTT configuration is an exception to the limitation of conventional cloud Wi-Fi sensors. The [hardware section](/oborudovanie/) explains why retail Tuya sensors generally cannot be routed to the MQTT GrowerHub, but the open source ESP32 can be integrated separately.
