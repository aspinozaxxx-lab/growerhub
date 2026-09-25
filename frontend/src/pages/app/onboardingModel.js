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

export const isLocalMqttValid = (local = {}) => {
  const host = String(local.host || '').trim();
  const port = String(local.port || '').trim();
  const baseTopic = String(local.baseTopic ?? 'zigbee2mqtt').trim().replace(/\/+$/u, '');
  return /^(?:[A-Za-z0-9_.-]+|\[[A-Fa-f0-9:]+\])$/u.test(host)
    && /^\d+$/u.test(port) && Number(port) > 0 && Number(port) <= 65535
    && /^[^\s#+]+$/u.test(baseTopic)
    && !/[\r\n]/u.test(String(local.username || '') + String(local.password || ''));
};

export const buildBridgeConfig = ({ setup, local, locale = 'ru' }) => {
  if (!setup?.username || !setup?.password || !setup?.client_id || !setup?.base_topic || !isLocalMqttValid(local)) return '';
  const baseTopic = (local.baseTopic ?? 'zigbee2mqtt').trim().replace(/\/+$/u, '');
  const localAuth = local.username
    ? `remote_username ${local.username}\nremote_password ${local.password || ''}\n`
    : '';
  const comments = BRIDGE_COMMENTS[locale] || BRIDGE_COMMENTS.ru;

  return `# ${comments.generated}\nconnection local-zigbee2mqtt\naddress ${String(local.host).trim()}:${String(local.port).trim()}\nbridge_protocol_version mqttv311\nstart_type automatic\ncleansession false\nnotifications false\ntry_private true\nremote_clientid ${setup.client_id}-local\n${localAuth}\n# ${comments.telemetry}\ntopic bridge/state in 1 relay/from-local/ ${baseTopic}/\ntopic bridge/info in 1 relay/from-local/ ${baseTopic}/\ntopic bridge/devices in 1 relay/from-local/ ${baseTopic}/\ntopic bridge/response/# in 1 relay/from-local/ ${baseTopic}/\ntopic + in 1 relay/from-local/ ${baseTopic}/\ntopic +/availability in 1 relay/from-local/ ${baseTopic}/\n\n# ${comments.commands}\ntopic +/set out 1 relay/to-local/ ${baseTopic}/\ntopic +/get out 1 relay/to-local/ ${baseTopic}/\ntopic bridge/request/# out 1 relay/to-local/ ${baseTopic}/\n\nconnection growerhub\naddress growerhub.ru:8883\nbridge_protocol_version mqttv311\nstart_type automatic\ncleansession false\nnotifications false\ntry_private true\nremote_clientid ${setup.client_id}\nbridge_cafile /etc/ssl/certs/ca-certificates.crt\nbridge_insecure false\nremote_username ${setup.username}\nremote_password ${setup.password}\n\n# ${comments.namespace}\ntopic bridge/state out 1 relay/from-local/ ${setup.base_topic}/\ntopic bridge/info out 1 relay/from-local/ ${setup.base_topic}/\ntopic bridge/devices out 1 relay/from-local/ ${setup.base_topic}/\ntopic bridge/response/# out 1 relay/from-local/ ${setup.base_topic}/\ntopic + out 1 relay/from-local/ ${setup.base_topic}/\ntopic +/availability out 1 relay/from-local/ ${setup.base_topic}/\ntopic +/set in 1 relay/to-local/ ${setup.base_topic}/\ntopic +/get in 1 relay/to-local/ ${setup.base_topic}/\ntopic bridge/request/# in 1 relay/to-local/ ${setup.base_topic}/\n`;
};
