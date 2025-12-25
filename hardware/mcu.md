# MCU — GPIO usage

MCU: ESP32 devboard  
Источник истины по GPIO: firmware (настройки / хардкод)

## Используемые сигналы
- SOIL_A_ADC
- SOIL_B_ADC
- PUMP_CTRL
- LIGHT_RELAY (Grow Light)
- RTC_SDA
- RTC_SCL
- DHT22_DATA (если используется)
- LED_STATUS (если используется)

## GPIO mapping
(заполняется по firmware)

| Signal | GPIO | Источник |
|---|---:|---|
| SOIL_A_ADC | GPIO34 | firmware/src/Application.cpp:410 |
| SOIL_B_ADC | GPIO__ | not found in firmware |
| PUMP_CTRL | GPIO4 | firmware/src/Application.cpp:434 |
| LIGHT_RELAY | GPIO5 | firmware/src/Application.cpp:435 |
| RTC_SDA | GPIO21 | firmware/src/System/SystemClock.h:15 |
| RTC_SCL | GPIO22 | firmware/src/System/SystemClock.h:16 |
| DHT22_DATA | GPIO15 | firmware/src/Application.cpp:413 |
| LED_STATUS | GPIO__ | not found in firmware |

Примечания:
- ADC входы должны работать от 0–3.3V
- Предпочтительно использовать ADC1 (если применимо)
