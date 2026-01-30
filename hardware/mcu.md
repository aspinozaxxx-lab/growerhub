# MCU — GPIO usage
## Используемые сигналы
- SOIL_A_ADC    |   Датчик влажности почвы (аналоговый)
- SOIL_B_ADC    |   Датчик влажности почвы (аналоговый)
- PUMP_CTRL     |   Реле (Mosfet) включаещее насос
- LIGHT_RELAY   |   Реле (Mosfet) включаещее освещение
- RTC_SDA       |   RTC модуль
- RTC_SCL       |   RTC модуль
- DHT22_DATA    |   Датчик температуры и влажности (цифровой)

## MCU: ESP32 devboard  
Signal      | GPIO 
SOIL_A_ADC  | GPIO34 
SOIL_B_ADC  | GPIO35 
PUMP_CTRL   | GPIO4 
LIGHT_RELAY | GPIO5 
RTC_SDA     | GPIO21 
RTC_SCL     | GPIO22 
DHT22_DATA  | GPIO15 

## MCU: ESP32-С3 Supermini  
Signal      | GPIO 
SOIL_A_ADC  | GPIO0 
SOIL_B_ADC  | not used 
PUMP_CTRL   | GPIO3 
LIGHT_RELAY | not used (заглушка на 11 пин)
RTC_SDA     | GPIO4 
RTC_SCL     | GPIO5 
DHT22_DATA  | GPIO1 
Мощьность wifi ограничена до 15dBm (иначе не подлючается)

