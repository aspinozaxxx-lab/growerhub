---
translation_of: zigbee-rozetka-dlya-sveta-i-nasosa
slug: smart-zigbee-socket-for-light-and-pump-capabilities-and-limitations
title: 'Smart Zigbee socket for light and pump: capabilities and limitations'
summary: >-
  When Zigbee socket is suitable for lamp, fan or pump, which loads are
  dangerous and what to check before automation.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: zigbee-hub-i-ustroystva
tags:
  - GrowerHub
  - Zigbee
  - socket
keywords:
  - smart socket Zigbee for plants
  - Zigbee relay
  - light automation
related:
  - avtomatizatsiya-sveta-v-groubokse
  - bezopasnyy-avtopoliv-limity-i-avariynyy-stop
  - zigbee-datchik-protechki-dlya-avtopoliva
  - roli-zigbee-ustroystv-v-growerhub
hero_image: /content/articles/illustrations/zigbee-rozetka-dlya-sveta-i-nasosa.webp
hero_alt: 'Illustration GrowerHub: Zigbee-socket controls lamp and pump with protection'
---
![Illustration GrowerHub: Zigbee-socket controls lamp and pump with protection](/content/articles/illustrations/zigbee-rozetka-dlya-sveta-i-nasosa.webp)

The Zigbee socket is useful for automating a light, fan or small pump, but it does not make any load safe. Plants often have water, humidity, extension cords, power supplies and enclosed boxes nearby. Therefore, before connecting, you need to check not only the application, but also the electrical part.

The socket is suitable when the load falls within the specification limits, the device operates under normal conditions and there is a clear failure logic. For powerful lamps, pumps with high starting current or humid environments, it is better to use a specialized relay, contactor and normal electrical protection.

## What to check for load

See the device power, starting current and operating time. A luminaire with a driver may have a high starting current. The pump may behave differently if the filter is clogged or running dry. The fan can run for hours. If the outlet gets hot, clicks unstably, or is in poor ventilation, this is already a risk.

Do not place the outlet under the reservoir, under drippers, or where water may enter. Even a leakage sensor does not change the normal placement of electrics. For automatic irrigation, water and electricity must be physically separated.

## Behavior after power failure

It is important for GrowerHub to know what will happen after a power outage. Will the outlet return to its last state, remain off, or turn on by default? These are different risks for the light and the pump. The light may go out for a day, the pump may turn on without a fresh check of the sensors.

Before automation, do a test: turn on the device, turn off the power, turn it back on and see the status. Record the result in the description of the device or rule. If the behavior is hazardous, do not use such an outlet for critical loads.

## Socket as a router Zigbee

Many mains-powered Zigbee sockets act as routers and help the mesh network. This is useful for the greenhouse: battery sensors can be connected via a nearby network device. But not every outlet routes equally well, and you shouldn’t install it just for the sake of the network if it’s not electrically suitable.

The roles of devices are discussed in the article [roles of Zigbee devices in GrowerHub](/articles/roli-zigbee-ustroystv-v-growerhub). To be on the safe side, it is best to plan coverage separately from hazardous load management.

## Logic for the pump

If the socket controls the pump, it needs restrictions: maximum duration of activation, pause, daily limit, stop for leakage and manual service mode. You cannot simply leave the pump on schedule without emergency blocking. Safety rules are described in the article [safe automatic watering](/articles/bezopasnyy-avtopoliv-limity-i-avariynyy-stop).

## Logic for light

For lights, scheduling, manual mode, and return after maintenance are important. If the user turned on the lamp manually, the system should indicate this and return to the staffing schedule after the specified time. Read more about this in the article [automation of light in a grow box](/articles/avtomatizatsiya-sveta-v-groubokse).

## Conclusion

A Zigbee socket is useful if the load is light, the location is dry, the behavior after a fault has been verified, and the GrowerHub rules limit dangerous conditions. For critical pumps and high-power lights, choose engineered controls rather than just a smart button in an app.
