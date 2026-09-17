---
translation_of: esp32-datchik-vlazhnosti-home-assistant
slug: soil-moisture-sensor-on-esp32-connection-to-esphome-and-home-assistant
title: 'ESP32 soil moisture sensor: ESPHome wiring, calibration and Home Assistant'
summary: >-
  Connect a soil moisture sensor to ESP32 or ESP32-C3, keep voltage and percentage
  readings in ESPHome, calibrate the probe and add it to Home Assistant.
created_at: '2026-07-23'
updated_at: "2026-09-17"
cluster: home-assistant-i-diy
tags:
  - GrowerHub
  - ESP32
  - Home Assistant
keywords:
  - ESP32 capacitive soil moisture sensor
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

An ESP32 and a capacitive probe can help monitor soil moisture. Treat the result as a **relative reading for your installation**. It depends on the board, power supply, sensor, cable, substrate and probe position. Start with a stable voltage reading, calibrate it in the pot, and observe several watering cycles before considering automatic control.

## What you need

| Component | Requirement |
|---|---|
| ESP32 | A working board with a suitable ADC input |
| Sensor | Preferably a capacitive probe without exposed galvanic electrodes |
| Power supply | Stable and compatible with the board and sensor; the signal stays within the ESP32 input limits |
| Enclosure | Keeps the ESP32 and connections away from water |
| Verification | A multimeter and manual checks of the substrate |

Check the documentation for your exact board and sensor before wiring them. Wire colors and labels in a seller's photograph are not enough to establish a pinout.

## ESP32 and ESP32-C3 SuperMini use different pins

The GPIO34 example below applies to the original ESP32, where that pin is an ADC1 input. **ESP32-C3 has no GPIO34:** ESPHome lists ADC1 on GPIO0–GPIO4. For a SuperMini, check the exact board pinout and whether the pin is exposed, then replace `pin`. The chip family alone does not verify a particular board layout. [ADC pin table](https://esphome.io/components/sensor/adc/#esp32-pins-and-hardware-details).

| Connection | Check before applying power |
|---|---|
| Sensor GND → board GND | Common ground |
| Sensor VCC → suitable supply | Sensor’s specified supply voltage |
| Analog OUT → selected ADC1 | Output stays within the board input limits |

These are connections by pin function, not a verified wiring diagram for every SuperMini board. Do not apply 5 V to an ADC input. A capacitive moisture probe does not measure EC or fertilizer concentration; that requires a separate instrument.

## Step 1: Keep a voltage reading

With `raw: false`, the ADC component reports volts, not a normalized value between 0 and 1. Switching to `raw: true` changes the scale, so calibration points cannot be reused between those modes. See ESPHome's [Analog To Digital Sensor](https://esphome.io/components/sensor/adc/) documentation.

Add this sensor section to an existing ESPHome device configuration. It is not a complete device file: keep your own board, Wi-Fi and API settings. If you already have a `sensor:` section, add the entry to its list.

```yaml
sensor:
  - platform: adc
    id: soil_voltage
    pin: GPIO34
    name: "Seedlings — sensor voltage"
    attenuation: auto
    raw: false
    unit_of_measurement: "V"
    device_class: voltage
    accuracy_decimals: 3
    update_interval: 30s
    filters:
      - median:
          window_size: 7
          send_every: 1
```

Choose the GPIO for your board. A median filter can reduce isolated spikes; it cannot repair an unstable power supply or a wet connector.

The two YAML blocks in this article pass the ESPHome 2026.9.0 configuration validator. Wiring and calibration still need to be verified on your hardware.

## Step 2: Check the installation

Insert the sensing area into the root zone, away from the pot wall and the point directly under a dripper. Keep the electronics dry and secure the cable. Moving the probe changes its contact with the substrate and can shift the reading.

Do three checks:

1. The signal is reasonably stable in air and in dry substrate.
2. After manual watering, it changes in a repeatable direction.
3. As the substrate dries, it moves back toward its earlier value.

If it responds mainly when you move the cable, check the electrical connection before interpreting the result as moisture.

## Step 3: Collect calibration points

Do not use a glass of water as your only “100%” reference. Collect readings from actual watering cycles in the substrate where the probe will stay:

| State | What to write down |
|---|---|
| Before a normal manual watering | Voltage and the plant's condition |
| After water has spread and excess has drained | Voltage at the wetter reference point |
| The following day | How quickly the reading changes |
| Before the next watering | Whether the drier reference point is repeatable |

Use two repeatable reference points to define a **relative scale for this installation**. This is not a laboratory measurement of volumetric water content.

Keep the voltage sensor and add a percentage reading with [Copy Sensor](https://esphome.io/components/copy/#copy-sensor). Append the entry below to the same `sensor:` list, after the ADC entry; do not create a second `sensor:` key. **Replace 0.82 and 0.39 with your measured voltages before using the percentage.**

```yaml
  - platform: copy
    source_id: soil_voltage
    name: "Seedlings — soil moisture"
    unit_of_measurement: "%"
    device_class: moisture
    state_class: measurement
    accuracy_decimals: 0
    filters:
      - calibrate_linear:
          method: exact
          datapoints:
            - 0.39 -> 100.0
            - 0.82 -> 0.0
      - clamp:
          min_value: 0
          max_value: 100
```

Here, 0.82 V represents the drier point and 0.39 V the wetter point. Your probe may work in the opposite direction. Sort the voltages to the left of `->` in ascending order, even when the percentages decrease. [calibrate_linear](https://esphome.io/components/sensor/#calibrate-linear) performs the conversion; changing `unit_of_measurement` alone does not. The percentage entity explicitly uses the `moisture` [device class](https://www.home-assistant.io/integrations/sensor/#device-class).

The `clamp` filter limits the displayed range to 0–100 and can hide out-of-range readings. Keep the voltage history for diagnosis. A disconnected wire can also produce an extreme value: 0% alone is not a reason to start a pump.

## Step 4: Add the sensor to Home Assistant

For the native API, use the [ESPHome integration](https://www.home-assistant.io/integrations/esphome/):

1. Validate the configuration in ESPHome Device Builder and install it on your sensor.
2. In Home Assistant, open **Settings → Devices & services** and configure the discovered ESPHome device. If discovery does not find it, add the ESPHome integration using the device's local network address.
3. If API encryption is enabled, provide the key from that device's configuration.
4. Check both entities: voltage in V and relative soil moisture in %. Verify the response to manual watering and that the device becomes unavailable when disconnected.

MQTT is another option when a system understands the chosen topics and payloads. MQTT support alone does not make an ESPHome sensor compatible with GrowerHub.

For MQTT, configure state and availability. Automation should reject stale readings rather than keep using the last known value. See the Home Assistant [MQTT integration](https://www.home-assistant.io/integrations/mqtt/).

## Step 5: Observe before automating

Observe the sensor for several watering cycles without automatic control. Compare its chart with manual watering, pot weight and the substrate's condition. If you keep changing the threshold each day, collect more evidence before letting it control a pump.

Keep the latest reading, its age, the chart and watering records together. Retain the voltage reading so you can investigate changes in the measurement itself.

## Try reading a soil moisture chart before buying hardware

Open the [GrowerHub demo farm](/app/demo/?lang=en&view=overview) and select a greenhouse's soil moisture reading. View the past day, then compare it with another greenhouse. This lets you try the interface while your ESP32 is still collecting its first measurements.

No account or equipment is needed. The four greenhouses contain virtual devices and simulated data; the demo does not calibrate your physical probe. Guest changes last 24 hours, and you can save the demo farm to an account.

## Troubleshooting unexpected readings

| Symptom | What to check |
|---|---|
| Always 0% or 100% | Inspect the voltage: calibration points may use a different scale, or `clamp` may be limiting the result |
| Percentage falls after watering | Check which calibration point is dry and which is wet |
| Voltage jumps when the cable moves | Check common ground, connections, power and probe placement |
| The entity exists but stops updating | Check ESP32 availability, ESPHome logs and its connection to Home Assistant |

Calibration cannot fix a broken connection, unstable power or the wrong GPIO.

## Can the same ESP32 control a pump?

A controller can read a sensor and operate a pump, but reliable measurement does not establish safe pump control. The load needs suitable power, switching protection, a dry enclosure, manual isolation and an independent run-time limit. Restarting the ESP32 or Home Assistant must not leave a pump running.

Until those safeguards are verified, use the ESP32 for monitoring. Before adding automatic watering, arrange leak detection, measure the pump's flow and review [watering limits and emergency stops](/articles/bezopasnyy-avtopoliv-limity-i-avariynyy-stop/).

## Result checklist

- The signal stays within the ESP32 input limits.
- The controller and connections stay dry.
- Voltage readings are stable.
- The probe is fixed in the working substrate.
- Calibration is based on several real watering cycles.
- Home Assistant shows the device as unavailable when it loses connection.
- Pump control does not rely on a single software timer.

The percentage can then serve as a useful reference for that particular zone.

The supplied GrowerHub connector reads Zigbee2MQTT; native controllers use their own protocol. Arbitrary ESPHome/MQTT topics are not imported automatically and require a compatible adapter. The supported connection path is explained in the [Zigbee2MQTT and Home Assistant guide](/articles/growerhub-i-home-assistant-cherez-mqtt/).
