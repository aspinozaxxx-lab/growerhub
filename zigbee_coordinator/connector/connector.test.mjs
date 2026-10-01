import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { bridgeInfo, inventoryRoutes, startConnector, validateConfig } from './connector.mjs';

const config = {
  v: 1,
  local: { host: '127.0.0.1', port: 1883, base_topic: 'greenhouse/z2m', username: 'local', password: 'local-secret' },
  cloud: { host: 'growerhub.ru', port: 8883, base_topic: 'gh/z2m/relay_a', username: 'relay_a', password: 'cloud-secret', client_id: 'relay_a' },
};
const inventory = ['flat', 'garden/nested', 'теплица/почва', 'suffix/set'].map((friendly_name, index) => ({ friendly_name, ieee_address: `0x${index}` }));

class Client extends EventEmitter {
  connected = false;
  published = [];
  subscriptions = [];
  unsubscribed = [];
  subscribe(filters, options, done) { this.subscriptions.push({ filters, options }); done(null, filters.map((topic) => ({ topic, qos: 0 }))); }
  unsubscribe(filters) { this.unsubscribed.push(...filters); }
  publish(topic, payload, options, done = () => {}) { this.published.push({ topic, payload, ...options }); done(); }
  end(force, done) { this.connected = false; done(); }
  connect() { this.connected = true; this.emit('connect'); }
  disconnect() { this.connected = false; this.emit('close'); }
  message(topic, payload, retain = false, dup = false) { this.emit('message', topic, Buffer.from(payload), { retain, dup }); }
}
function setup(overrides = {}) {
  const clients = [];
  const options = [];
  const logs = [];
  const connector = startConnector({ ...config, ...overrides }, {
    connect: (option) => { options.push(option); const client = new Client(); clients.push(client); return client; },
    log: (code) => logs.push(code),
  });
  connector.cloud.connect();
  connector.local.connect();
  connector.local.message('greenhouse/z2m/bridge/devices', JSON.stringify(inventory), true);
  return { ...connector, options, logs };
}

test('full names, reserved routes and ambiguity fail closed', () => {
  const routes = inventoryRoutes([...inventory, { friendly_name: 'garden' }, { friendly_name: 'garden/availability' },
    { friendly_name: 'duplicate' }, { friendly_name: 'duplicate' }, { friendly_name: 'bridge/state' }, { friendly_name: 'invalid/+' }]);
  assert(routes.states.has('garden/nested'));
  assert(routes.states.has('теплица/почва/availability'));
  assert(routes.commands.has('suffix/set/set'));
  assert(!routes.states.has('garden'));
  assert(!routes.states.has('garden/availability'));
  assert(!routes.states.has('duplicate'));
  assert(!routes.states.has('bridge/state'));
  assert(!routes.states.has('invalid/+'));
});

test('bridge info never leaks nested configuration or credentials', () => {
  const sanitized = bridgeInfo({ version: '2.12.0', permit_join: false, config: { mqtt: { password: 'secret' } },
    network_key: 'network-secret', coordinator: { type: 'zstack', ieee_address: '0x1', secret: 'secret',
      meta: { revision: 20250101, network_key: 'secret', version: { secret: 'secret' } } } });
  assert.equal(sanitized.coordinator.meta.revision, 20250101);
  assert(!JSON.stringify(sanitized).includes('secret'));
  assert.deepEqual(bridgeInfo({ version: '2.12.0' }), { version: '2.12.0' });
});

test('isolated identity, TLS, clean sessions and all client queues are explicit', () => {
  const connector = setup();
  const [cloud, local] = connector.options;
  assert.equal(cloud.clientId, 'relay_a');
  assert.equal(local.clientId, 'relay_a-local');
  assert.equal(cloud.protocol, 'mqtts');
  assert.equal(cloud.rejectUnauthorized, true);
  assert.equal(cloud.protocolVersion, 5);
  for (const options of connector.options) {
    assert.equal(options.clean, true);
    assert.equal(options.resubscribe, false);
    assert.equal(options.queueQoSZero, false);
  }
  for (const sub of connector.cloud.subscriptions) {
    assert.equal(sub.options.qos, 0);
    assert.equal(sub.options.rap, true);
    assert.equal(sub.options.rh, 2);
    assert(sub.filters.every((topic) => topic.startsWith('gh/z2m/relay_a/') && !topic.includes('#') && !topic.includes('+')));
  }
  assert.throws(() => validateConfig({ ...config, cloud: { ...config.cloud, base_topic: 'gh/z2m/other' } }));
});

test('known state and availability preserve source retain; commands do not echo', () => {
  const connector = setup();
  connector.cloud.published.length = 0;
  for (const name of ['flat', 'garden/nested', 'теплица/почва']) {
    connector.local.message(`greenhouse/z2m/${name}`, '{"temperature":23}', true);
    connector.local.message(`greenhouse/z2m/${name}/availability`, '{"state":"online"}');
    connector.local.message(`greenhouse/z2m/${name}/set`, '{"state":"ON"}');
  }
  connector.local.message('greenhouse/z2m/unknown', '{"temperature":99}');
  connector.local.message('greenhouse/z2m/flat/temperature', '99');
  connector.local.message('greenhouse/z2m/bridge/config', '{"password":"secret"}');
  assert.equal(connector.cloud.published.length, 6);
  assert.deepEqual(JSON.parse(connector.cloud.published[0].payload), { v: 1, payload: '{"temperature":23}', retained: true });
  assert.equal(connector.cloud.published[0].topic, 'gh/z2m/relay_a/bridge/relay/flat');
});

test('add, rename, removal and conflicting names replace subscriptions without config changes', () => {
  const connector = setup();
  connector.local.message('greenhouse/z2m/bridge/devices', '[{"friendly_name":"new/name"}]');
  connector.cloud.published.length = 0;
  connector.local.message('greenhouse/z2m/flat', '{}');
  connector.local.message('greenhouse/z2m/new/name', '{"power":10}');
  connector.cloud.message('gh/z2m/relay_a/flat/set', '{"state":"ON"}');
  connector.cloud.message('gh/z2m/relay_a/new/name/set', '{"state":"ON"}');
  assert.equal(connector.cloud.published.length, 1);
  assert.equal(connector.local.published.length, 1);
  assert(connector.local.unsubscribed.includes('greenhouse/z2m/flat'));
  connector.local.message('greenhouse/z2m/bridge/devices', '[{"friendly_name":"a"},{"friendly_name":"a/get"}]');
  connector.cloud.message('gh/z2m/relay_a/a/get', '{}');
  assert.equal(connector.local.published.length, 1);
  assert(connector.logs.includes('ambiguous_names:2'));
});

test('only own, live, nonduplicate commands pass once and disconnects have no queue', () => {
  const connector = setup();
  const command = 'gh/z2m/relay_a/garden/nested/set';
  connector.cloud.message(command, '{"state":"ON"}');
  connector.cloud.message(command, '{"state":"ON"}', true);
  connector.cloud.message(command, '{"state":"ON"}', false, true);
  connector.cloud.message('gh/z2m/other/garden/nested/set', '{"state":"ON"}');
  connector.cloud.message('gh/z2m/relay_a/unknown/set', '{"state":"ON"}');
  assert.equal(connector.local.published.length, 1);
  assert.equal(connector.local.published[0].qos, 0);
  assert.equal(connector.local.published[0].retain, false);
  connector.local.disconnect();
  connector.cloud.message(command, '{"state":"ON"}');
  connector.local.connect();
  connector.cloud.message(command, '{"state":"ON"}');
  assert.equal(connector.local.published.length, 1);
  connector.local.message('greenhouse/z2m/bridge/devices', JSON.stringify(inventory), true);
  connector.cloud.disconnect();
  connector.cloud.message(command, '{"state":"ON"}');
  connector.cloud.connect();
  assert.equal(connector.local.published.length, 1);
});

test('invalid UTF8 and oversize source payloads are rejected without secret logs', () => {
  const connector = setup({ limits: { max_message_bytes: 500 } });
  const before = connector.cloud.published.length;
  connector.local.emit('message', 'greenhouse/z2m/flat', Buffer.from([255]), { retain: false });
  connector.local.message('greenhouse/z2m/flat', 'secret'.repeat(200));
  assert.equal(connector.cloud.published.length, before);
  assert(!connector.logs.join(' ').includes('secret'));
});

test('malformed inventory disables commands until a valid snapshot arrives', () => {
  const connector = setup();
  connector.local.message('greenhouse/z2m/bridge/devices', '{');
  connector.cloud.message('gh/z2m/relay_a/flat/set', '{"state":"ON"}');
  assert.equal(connector.local.published.length, 0);
  connector.local.message('greenhouse/z2m/bridge/devices', JSON.stringify(inventory));
  connector.cloud.message('gh/z2m/relay_a/flat/set', '{"state":"ON"}');
  assert.equal(connector.local.published.length, 1);
});
