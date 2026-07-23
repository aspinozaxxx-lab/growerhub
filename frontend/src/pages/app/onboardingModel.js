export const CONNECTION_MODES = {
  DIRECT: 'direct',
  BRIDGE: 'bridge',
};

export const SETUP_PLATFORMS = {
  WINDOWS: 'windows',
  LINUX: 'linux',
  MANUAL: 'manual',
};

export const getReadableFeatures = (overview, property) => (overview?.devices || [])
  .flatMap((device) => (device.metrics || [])
    .filter((feature) => !property || feature.property === property)
    .map((feature) => ({ device, feature })));

export const getWritableSwitches = (overview) => (overview?.devices || [])
  .flatMap((device) => (device.controls || [])
    .filter((feature) => feature.property === 'state')
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

export const buildSectionResources = ({ coordinatorId, temperatureChoice, lightChoice, overview }) => {
  const resources = [];
  const temperature = decodeFeatureChoice(temperatureChoice);
  const light = decodeFeatureChoice(lightChoice);

  if (temperature) {
    resources.push({
      role: 'AIR_TEMPERATURE_SENSOR',
      source_type: 'ZIGBEE_DEVICE',
      zigbee_coordinator_id: coordinatorId,
      zigbee_ieee_address: temperature.ieee_address,
      zigbee_property: temperature.property,
    });
  }

  if (light) {
    const match = getWritableSwitches(overview).find(({ device, feature }) => (
      device.ieee_address === light.ieee_address && feature.property === light.property
    ));
    resources.push({
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

const BRIDGE_COMMENTS = {
  ru: {
    generated: 'Создано в браузере GrowerHub. Локальные учётные данные MQTT не отправлялись на сервер.',
    telemetry: 'Телеметрия: только из локального Zigbee2MQTT.',
    commands: 'Команды: только обратно в локальный Zigbee2MQTT.',
    namespace: 'Изолированное пространство GrowerHub.',
  },
  en: {
    generated: 'Generated in the GrowerHub browser interface. Local MQTT credentials were not sent to the server.',
    telemetry: 'Telemetry: only from the local Zigbee2MQTT instance.',
    commands: 'Commands: only back to the local Zigbee2MQTT instance.',
    namespace: 'Isolated GrowerHub namespace.',
  },
};

export const buildBridgeConfig = ({ setup, local, locale = 'ru' }) => {
  if (!setup?.username || !setup?.password || !setup?.base_topic) return '';
  const localAuth = local.username
    ? `remote_username ${local.username}\nremote_password ${local.password || ''}\n`
    : '';
  const comments = BRIDGE_COMMENTS[locale] || BRIDGE_COMMENTS.ru;

  return `# ${comments.generated}\nconnection local-zigbee2mqtt\naddress ${local.host || '127.0.0.1'}:${local.port || '1883'}\nbridge_protocol_version mqttv311\nstart_type automatic\ncleansession false\nnotifications false\ntry_private true\nremote_clientid growerhub-connector-local\n${localAuth}\n# ${comments.telemetry}\ntopic bridge/state in 1 relay/from-local/ zigbee2mqtt/\ntopic bridge/info in 1 relay/from-local/ zigbee2mqtt/\ntopic bridge/devices in 1 relay/from-local/ zigbee2mqtt/\ntopic bridge/response/# in 1 relay/from-local/ zigbee2mqtt/\ntopic + in 1 relay/from-local/ zigbee2mqtt/\ntopic +/availability in 1 relay/from-local/ zigbee2mqtt/\n\n# ${comments.commands}\ntopic +/set out 1 relay/to-local/ zigbee2mqtt/\ntopic +/get out 1 relay/to-local/ zigbee2mqtt/\ntopic bridge/request/# out 1 relay/to-local/ zigbee2mqtt/\n\nconnection growerhub\naddress growerhub.ru:8883\nbridge_protocol_version mqttv311\nstart_type automatic\ncleansession false\nnotifications false\ntry_private true\nremote_clientid ${setup.client_id}\nbridge_cafile /etc/ssl/certs/ca-certificates.crt\nbridge_insecure false\nremote_username ${setup.username}\nremote_password ${setup.password}\n\n# ${comments.namespace}\ntopic bridge/state out 1 relay/from-local/ ${setup.base_topic}/\ntopic bridge/info out 1 relay/from-local/ ${setup.base_topic}/\ntopic bridge/devices out 1 relay/from-local/ ${setup.base_topic}/\ntopic bridge/response/# out 1 relay/from-local/ ${setup.base_topic}/\ntopic + out 1 relay/from-local/ ${setup.base_topic}/\ntopic +/availability out 1 relay/from-local/ ${setup.base_topic}/\ntopic +/set in 1 relay/to-local/ ${setup.base_topic}/\ntopic +/get in 1 relay/to-local/ ${setup.base_topic}/\ntopic bridge/request/# in 1 relay/to-local/ ${setup.base_topic}/\n`;
};
