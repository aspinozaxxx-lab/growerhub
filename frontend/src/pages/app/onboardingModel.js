export const getReadableFeatures = (overview, property) => (overview?.devices || [])
  .flatMap((device) => (device.metrics || [])
    .filter((feature) => !property || feature.property === property)
    .map((feature) => ({ device, feature })));

export const getWritableSwitches = (overview) => (overview?.devices || [])
  .flatMap((device) => (device.controls || [])
    .filter((feature) => feature.property === 'state'
      && !(device.watering || []).some((capability) => capability.property === feature.property))
    .map((feature) => ({ device, feature })));

export const encodeFeatureChoice = (device, feature) => JSON.stringify({
  ieee_address: device.ieee_address,
  property: feature.property,
});

export const decodeFeatureChoice = (value) => {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value);
    if (!parsed?.ieee_address || !parsed?.property) return null;
    return parsed;
  } catch {
    return null;
  }
};

export const buildSectionResources = ({ coordinatorId, temperatureChoice, humidityChoice, soilMoistureChoice, lightChoice, overview }) => {
  const resources = [];
  const light = decodeFeatureChoice(lightChoice);

  for (const [role, value] of [
    ['AIR_TEMPERATURE_SENSOR', temperatureChoice],
    ['AIR_HUMIDITY_SENSOR', humidityChoice],
    ['SOIL_MOISTURE_SENSOR', soilMoistureChoice],
  ]) {
    const choice = decodeFeatureChoice(value);
    if (choice) {
      resources.push({
        role,
        source_type: 'ZIGBEE_DEVICE',
        zigbee_coordinator_id: coordinatorId,
        zigbee_ieee_address: choice.ieee_address,
        zigbee_property: choice.property,
      });
    }
  }

  if (light) {
    const match = getWritableSwitches(overview).find(({ device, feature }) => (
      device.ieee_address === light.ieee_address && feature.property === light.property
    ));
    if (match) resources.push({
      role: 'LIGHT_SWITCH',
      source_type: 'ZIGBEE_DEVICE',
      zigbee_coordinator_id: coordinatorId,
      zigbee_ieee_address: light.ieee_address,
      command_property: light.property,
      on_value: match?.feature?.value_on || 'ON',
      off_value: match?.feature?.value_off || 'OFF',
    });
  }

  return resources;
};
