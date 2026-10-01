import mqtt from 'mqtt';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const BRIDGE_COMMANDS = ['permit_join', 'device/rename'];
const BRIDGE_TOPICS = ['bridge/state', 'bridge/info', 'bridge/devices',
  ...BRIDGE_COMMANDS.map((name) => `bridge/response/${name}`)];
const decoder = new TextDecoder('utf-8', { fatal: true });

export const validName = (name) => typeof name === 'string' && name.trim() !== ''
  && name.length <= 255 && name !== 'bridge' && !name.startsWith('bridge/')
  && !/[\u0000-\u001f\u007f#+]/u.test(name);

export function inventoryRoutes(inventory) {
  if (!Array.isArray(inventory)) throw new Error('invalid_inventory');
  const names = new Set();
  const ambiguous = new Set();
  for (const device of inventory) {
    if (!validName(device?.friendly_name)) continue;
    if (names.has(device.friendly_name)) ambiguous.add(device.friendly_name);
    names.add(device.friendly_name);
  }
  const owners = new Map();
  for (const name of names) {
    for (const suffix of ['', '/availability', '/set', '/get']) {
      const previous = owners.get(name + suffix);
      if (previous && previous !== name) {
        ambiguous.add(previous);
        ambiguous.add(name);
      }
      owners.set(name + suffix, name);
    }
  }
  const states = new Set();
  const commands = new Set();
  for (const name of names) {
    if (ambiguous.has(name)) continue;
    states.add(name);
    states.add(`${name}/availability`);
    const device = inventory.find((item) => item?.friendly_name === name);
    if (device?.type !== 'Coordinator' && !device?.disabled) {
      commands.add(`${name}/set`);
      commands.add(`${name}/get`);
    }
  }
  return { states, commands, ambiguous };
}

export function bridgeInfo(payload) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new Error('invalid_bridge_info');
  const result = {};
  if (typeof payload.version === 'string') result.version = payload.version;
  if (typeof payload.permit_join === 'boolean') result.permit_join = payload.permit_join;
  if (payload.permit_join_end === null || Number.isFinite(payload.permit_join_end)) result.permit_join_end = payload.permit_join_end;
  const coordinator = payload.coordinator;
  if (coordinator && typeof coordinator === 'object' && !Array.isArray(coordinator)) {
    result.coordinator = {};
    for (const key of ['ieee_address', 'type']) {
      if (typeof coordinator[key] === 'string') result.coordinator[key] = coordinator[key];
    }
    if (coordinator.meta && typeof coordinator.meta === 'object') {
      result.coordinator.meta = {};
      for (const key of ['maintrel', 'majorrel', 'minorrel', 'product', 'revision', 'transportrev', 'version']) {
        const value = coordinator.meta[key];
        if (typeof value === 'string' || (typeof value === 'number' && Number.isFinite(value))) result.coordinator.meta[key] = value;
      }
    }
  }
  return result;
}

export function validateConfig(config) {
  const hostValid = (host) => typeof host === 'string' && /^(?:[A-Za-z0-9_.-]+|\[[A-Fa-f0-9:]+\])$/u.test(host);
  const topicValid = (topic) => typeof topic === 'string' && topic !== '' && !/[\s#+\u0000-\u001f\u007f]/u.test(topic)
    && !topic.endsWith('/');
  for (const connection of [config?.local, config?.cloud]) {
    if (!connection || !hostValid(connection.host) || !Number.isInteger(connection.port)
        || connection.port < 1 || connection.port > 65535 || !topicValid(connection.base_topic)
        || (connection.username !== undefined && typeof connection.username !== 'string')
        || (connection.password !== undefined && typeof connection.password !== 'string')) throw new Error('invalid_connection_config');
  }
  if (config.v !== 1 || typeof config.cloud.username !== 'string' || !/^[A-Za-z0-9_-]+$/u.test(config.cloud.username)
      || !config.cloud.password || config.cloud.client_id !== config.cloud.username
      || config.cloud.base_topic !== `gh/z2m/${config.cloud.username}`) throw new Error('invalid_cloud_identity');
  if (config.cloud.ca_file !== undefined && (typeof config.cloud.ca_file !== 'string' || !config.cloud.ca_file)) throw new Error('invalid_ca_file');
  const limits = { max_message_bytes: 1048576, reconnect_ms: 5000, connect_timeout_ms: 30000, keepalive_seconds: 30, ...config.limits };
  for (const value of Object.values(limits)) {
    if (!Number.isSafeInteger(value) || value < 1) throw new Error('invalid_limits');
  }
  return { ...config, limits };
}

export function startConnector(configuration, { connect = mqtt.connect, log = (code) => console.log(code) } = {}) {
  const config = validateConfig(configuration);
  let routes = inventoryRoutes([]);
  let localFilters = new Set();
  let cloudFilters = new Set();
  let generation = 0;
  let commandReady = false;
  let stopped = false;
  const envelope = (payload, retained) => JSON.stringify({ v: 1, payload, retained });
  const options = (connection, clientId) => ({
    host: connection.host.replace(/^\[|\]$/gu, ''), port: connection.port,
    username: connection.username, password: connection.password, clientId,
    protocolVersion: 4, clean: true, resubscribe: false, queueQoSZero: false,
    reconnectPeriod: config.limits.reconnect_ms, connectTimeout: config.limits.connect_timeout_ms,
    keepalive: config.limits.keepalive_seconds,
  });
  const cloud = connect({ ...options(config.cloud, config.cloud.client_id), protocol: 'mqtts', protocolVersion: 5, rejectUnauthorized: true,
    ...(config.cloud.ca_file ? { ca: readFileSync(config.cloud.ca_file) } : {}),
    will: { topic: `${config.cloud.base_topic}/bridge/relay/bridge/state`,
      payload: envelope('{"state":"offline"}', false), qos: 0, retain: true },
  });
  const local = connect({ ...options(config.local, `${config.cloud.client_id}-local`), protocol: 'mqtt' });
  const connected = () => !stopped && cloud.connected && local.connected;
  const publish = (client, topic, payload, retain) => {
    if (!connected()) return;
    client.publish(topic, payload, { qos: 0, retain }, (error) => { if (error) log('publish_failed'); });
  };
  const forward = (relative, payload, retained, store = true) => {
    const encoded = envelope(payload, retained);
    if (Buffer.byteLength(encoded) > config.limits.max_message_bytes) { log('relay_too_large'); return; }
    publish(cloud, `${config.cloud.base_topic}/bridge/relay/${relative}`, encoded, store);
  };
  const subscribe = (client, filters, token, done = () => {}) => {
    if (!filters.length) { done(); return; }
    client.subscribe(filters, { qos: 0, ...(client === cloud ? { rap: true, rh: 2 } : {}) }, (error, granted) => {
      if (token !== generation || !connected()) return;
      if (error || granted?.some((item) => item.qos === 128)) { log('subscribe_failed'); return; }
      done();
    });
  };
  const updateRoutes = (inventory, payload, retained) => {
    commandReady = false;
    const token = ++generation;
    routes = inventoryRoutes(inventory);
    if (routes.ambiguous.size) log(`ambiguous_names:${routes.ambiguous.size}`);
    const nextLocal = new Set([...routes.states].map((topic) => `${config.local.base_topic}/${topic}`));
    const nextCloud = new Set([...routes.commands, ...BRIDGE_COMMANDS.map((name) => `bridge/request/${name}`)]
      .map((topic) => `${config.cloud.base_topic}/${topic}`));
    if (local.connected) {
      const removed = [...localFilters].filter((topic) => !nextLocal.has(topic));
      if (removed.length) local.unsubscribe(removed);
    }
    if (cloud.connected) {
      const removed = [...cloudFilters].filter((topic) => !nextCloud.has(topic));
      if (removed.length) cloud.unsubscribe(removed);
    }
    localFilters = nextLocal;
    cloudFilters = nextCloud;
    forward('bridge/devices', payload, retained);
    if (!connected()) return;
    subscribe(cloud, [...nextCloud], token, () => {
      subscribe(local, [...nextLocal], token, () => { commandReady = true; log('ready'); });
    });
  };
  const synchronize = () => {
    commandReady = false;
    const token = ++generation;
    if (!connected()) return;
    // Povtornaja podpiska vozvrashchaet snapshot s ishodnym RETAIN, a ne iz klientskogo cache.
    subscribe(local, BRIDGE_TOPICS.map((topic) => `${config.local.base_topic}/${topic}`), token);
  };
  local.on('connect', () => { log('local_connected'); synchronize(); });
  cloud.on('connect', () => { log('cloud_connected'); synchronize(); });
  local.on('close', () => {
    commandReady = false;
    generation++;
    if (cloud.connected && !stopped) cloud.publish(`${config.cloud.base_topic}/bridge/relay/bridge/state`,
      envelope('{"state":"offline"}', false), { qos: 0, retain: true });
    log('local_disconnected');
  });
  cloud.on('close', () => { commandReady = false; generation++; log('cloud_disconnected'); });
  local.on('error', () => log('local_connection_failed'));
  cloud.on('error', () => log('cloud_connection_failed'));
  local.on('message', (topic, buffer, packet) => {
    if (!connected() || !topic.startsWith(`${config.local.base_topic}/`)) return;
    const relative = topic.slice(config.local.base_topic.length + 1);
    if (!BRIDGE_TOPICS.includes(relative) && !routes.states.has(relative)) return;
    if (relative === 'bridge/devices') {
      commandReady = false;
      generation++;
    }
    if (buffer.length > config.limits.max_message_bytes) {
      if (relative === 'bridge/devices') routes = inventoryRoutes([]);
      log('message_too_large');
      return;
    }
    try {
      let payload = decoder.decode(buffer);
      if (relative === 'bridge/devices') {
        updateRoutes(JSON.parse(payload), payload, Boolean(packet.retain));
        return;
      }
      if (relative === 'bridge/info') payload = JSON.stringify(bridgeInfo(JSON.parse(payload)));
      const response = relative.startsWith('bridge/response/');
      if (response && packet.retain) return;
      forward(relative, payload, Boolean(packet.retain), !response);
    } catch {
      if (relative === 'bridge/devices') routes = inventoryRoutes([]);
      log('invalid_local_message');
    }
  });
  cloud.on('message', (topic, buffer, packet) => {
    if (!connected() || !commandReady || packet.retain || packet.dup
        || !topic.startsWith(`${config.cloud.base_topic}/`)) return;
    const relative = topic.slice(config.cloud.base_topic.length + 1);
    if (!routes.commands.has(relative) && !BRIDGE_COMMANDS.some((name) => relative === `bridge/request/${name}`)) return;
    if (buffer.length > config.limits.max_message_bytes) { log('command_too_large'); return; }
    try { publish(local, `${config.local.base_topic}/${relative}`, decoder.decode(buffer), false); }
    catch { log('invalid_command'); }
  });
  return {
    local, cloud,
    stop() {
      stopped = true;
      commandReady = false;
      generation++;
      return Promise.all([new Promise((done) => local.end(true, done)), new Promise((done) => cloud.end(true, done))]);
    },
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const connector = startConnector(JSON.parse(readFileSync(process.argv[2] || '/app/connector.json', 'utf8')));
    for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, () => connector.stop().then(() => process.exit(0)));
  } catch { console.error('invalid_config_or_ca'); process.exitCode = 1; }
}
