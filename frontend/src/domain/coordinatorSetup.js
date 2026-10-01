export const CONNECTION_MODES = {
  DIRECT: 'direct',
  BRIDGE: 'bridge',
};

export const SETUP_PLATFORMS = {
  WINDOWS: 'windows',
  LINUX: 'linux',
  MANUAL: 'manual',
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

export const buildConnectorConfig = ({ setup, local }) => {
  if (!setup?.username || !setup?.password || setup.client_id !== setup.username
      || setup.base_topic !== `gh/z2m/${setup.username}` || !isLocalMqttValid(local)) return '';
  let server;
  try { server = new URL(setup.server); } catch { return ''; }
  if (!['mqtts:', 'ssl:'].includes(server.protocol) || server.username || server.password
      || (server.pathname && server.pathname !== '/') || server.search || server.hash) return '';
  return JSON.stringify({
    v: 1,
    local: {
      host: String(local.host).trim(),
      port: Number(String(local.port).trim()),
      base_topic: String(local.baseTopic ?? 'zigbee2mqtt').trim().replace(/\/+$/u, ''),
      ...(local.username ? { username: local.username, password: local.password || '' } : {}),
    },
    cloud: {
      host: server.hostname, port: Number(server.port || 8883),
      username: setup.username, password: setup.password,
      client_id: setup.client_id, base_topic: setup.base_topic,
    },
    limits: { max_message_bytes: 1048576, reconnect_ms: 5000, connect_timeout_ms: 30000, keepalive_seconds: 30 },
  }, null, 2) + '\n';
};
