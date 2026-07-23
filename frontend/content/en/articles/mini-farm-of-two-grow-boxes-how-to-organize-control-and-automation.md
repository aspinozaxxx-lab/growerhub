---
translation_of: mini-ferma-iz-dvuh-grouboksov-dashboard
slug: mini-farm-of-two-grow-boxes-how-to-organize-control-and-automation
title: 'Mini-farm of two grow boxes: how to organize control and automation'
summary: >-
  Using the GrowerHub dashboard as an example, let’s look at a mini-farm
  consisting of two growboxes: climate, light, airflow, watering, sensors,
  plants and access via the Internet or local server.
created_at: '2026-07-23'
updated_at: '2026-07-23'
cluster: mini-ferma-i-neskolko-boksov
tags:
  - GrowerHub
  - small farm
  - grow box
  - dashboard
keywords:
  - small farm from grow boxes
  - grow box automation
  - small farm dashboard
  - greenhouse control via the Internet
  - local server for the farm
related:
  - monitoring-neskolkih-boksov
  - masshtabirovanie-ot-boksa-do-mini-fermy
  - avtomatizatsiya-teplitsy-chto-kontrolirovat
  - zigbee-dlya-teplitsy-kakie-ustroystva-polezny
hero_image: /content/articles/illustrations/mini-ferma-iz-dvuh-grouboksov-dashboard.png
hero_alt: >-
  Dashboard GrowerHub with a mini-farm of two growboxes, air conditioning,
  devices and sensors
---
![Dashboard GrowerHub with a mini-farm of two grow boxes, air conditioning, devices and sensors](/content/articles/illustrations/mini-ferma-iz-dvuh-grouboksov-dashboard.png)

A mini-farm of two grow boxes is no longer just “two cabinets with light.” Each box has its own character: in some places the temperature rises faster, in others the humidity lasts longer, in others airflow is needed more often, and in others it is better to leave watering on hold. If you look at all this as separate applications from sockets, sensors and relays, confusion begins very quickly.

The idea of GrowerHub is to put the farm together into one clear picture. The screenshot shows an example of such a mini-farm: there is a common “small farm” block, two boxes, a common air conditioner, requests for cooling, light, airflow, watering and sensors. This is not a beautiful widget for the sake of a widget, but a working panel that you can go to and understand what is happening in a few seconds.

And yes, all this is accessible via the Internet from anywhere in the world. You can open the dashboard from your phone while on the road, from a laptop in another city, or from your work computer if you need to quickly check the status of your farm.

## What can be seen from the dashboard example

The top part shows the overall farm. It’s convenient to keep everything here that relates not to one box, but to the entire installation at once. In the example, this is an air conditioner and requests for air conditioning from boxes. This approach is especially useful when one resource serves several zones: air conditioning, supply ventilation, a common tank, a common pump, a common server or a power source.

Below the farm is divided into boxes. In the first box the light is on, but the airflow and watering are not connected. In the second box, the airflow and light are turned on, watering is on hold, and sensors show air temperature and soil moisture. This is a good example of why boxes are best viewed as individual zones rather than as a general list of devices. It’s immediately clear where everything is connected, where what’s missing, and where the automation is already working.

The “Air Conditioner Requests” block is especially useful. If one of the boxes asks for cooling, it can be seen next to the air conditioner. The farm owner not only sees that the air conditioner is on, but understands the reason: for example, “Greenhouse 2 New” requested the climate. This is a small thing that greatly reduces anxiety. The system does not look like a black box.

## What can be automated

The most obvious layer is light. For each box, you can set a schedule, day and night modes, switching on via a socket or relay. On the dashboard you can see this as the “On” or “Off” state, and in the statistics you can see how long the device was actually turned on.

The next layer is blowing. The fan can be turned on according to a schedule, according to temperature, humidity or climate scenario. For example, if the humidity is consistently high at night, the air blower may work more often. If the temperature rises, the fan helps to even out the microclimate in the box.

It is better to automate watering carefully. You need a pump or valve, a soil moisture sensor, limits and a clear “waiting / watering” state. Good irrigation automation does not just turn on the pump, but takes into account duration, pauses, freshness of sensor data and emergency restrictions. If there is little data or the sensor has not been updated, it is better not to pretend that everything is under control.

The farm climate can be brought to a general level. In the example, the air conditioner is shared, and requests come from the boxes. This is a normal scheme for a small farm: each box monitors its own climate, and the common resource is turned on when it is really needed. In detail, this can work differently, but the logic is simple: the boxes report that they are hot, the farm makes a decision on the overall design.

## What is needed for this

The minimum set for such a farm looks quite mundane: two grow boxes, a light in each box, a fan or hood, temperature and humidity sensors, soil moisture sensors, a pump or valve for watering, and a device through which all this is connected to GrowerHub.

Some devices may be Zigbee: sockets, temperature and humidity sensors, leakage sensors, relays. Some may be native devices GrowerHub: controllers, sensors, pumps, executive modules. The possibilities for integrating different devices are constantly expanding, so the farm can be assembled gradually. You don't have to buy everything at once and build the perfect system in one evening.

A practical way is this: first connect the sensors and look at the history, then add light and airflow, then carefully turn on the watering and common resources like air conditioning. This way there is less risk and more understanding of what exactly is happening in each box.

## Plants inside boxes

You can add plants to the boxes. Then the box ceases to be just a set of devices and becomes a place for growing specific plants. Each plant is observed separately: it has its own cultivation log, its own notes, photographs, care events, watering and observation history.

This is convenient when you want to understand not only “the humidity in the box was 80%,” but “this plant looked better this week after changing the watering.” The plant can be moved between boxes and its history is not lost. Today it is in "Greenhouse 1", tomorrow it moved to "Greenhouse 2 New" - the magazine remains with the plant.

But this is not a required level of detail. If you don’t want to bother, you can run a farm at the box level: look at sensors, devices, watering and climate without separately accounting for each plant. GrowerHub does not force you to keep a laboratory diary where simple control is sufficient.

## How it works without unnecessary details

The devices send their status: the sensor sent the temperature, the socket reported that it was turned on, the pump sent the actual status, the climate scenario saw a request from the box. GrowerHub puts this data into a clear model: farm, boxes, resources, sensors, scenarios and history.

Then the interface shows not technical noise, but a human picture. There is no need to remember which outlet is called `smartplug4_exhaust_box2` if on the dashboard it is located in the “Greenhouse 2 New” block and labeled as “Blower”. Technical names remain for maintenance, but the operating screen speaks the language of the farm.

## Online and offline

For daily use, Internet access is most convenient. The dashboard can be opened from anywhere in the world, check the temperature, see the lights on, watering status, requests for air conditioning and the freshness of the data. This is especially useful if the farm is located at home, in the country, in a workshop or in a separate room.

In this case, another scheme is possible: managing the farm offline from a local server without access to the Internet. This option is suitable when autonomy, closed loop or unstable communication is important. The local server is located next to the equipment, collects data and runs scripts within the network. In this case, the Internet is not needed for basic work, but only if the owner himself decides to open remote access.

## Where to start

If you have two boxes, don't try to automate everything at once. Start with a map: what resources are shared, what devices belong to each box, what sensors are actually needed for solutions. Then connect monitoring, make sure that the data is fresh and understandable. After that, add automation of light, blowing, watering and climate.

A good dashboard shouldn't be intimidating. He must calmly answer simple questions: where is it hot, where is it humid, what is turned on, what is not connected, who is asking for air conditioning, when was the last signal from the sensor and whether it is necessary to intervene. On this basis, a mini-farm of two grow boxes becomes not a collection of wires and applications, but a managed system that can be developed step by step.
