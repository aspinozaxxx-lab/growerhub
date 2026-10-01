import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn, execFileSync } from 'node:child_process';
import mqtt from 'mqtt';
import { startConnector } from './connector.mjs';

const directory = mkdtempSync(join(tmpdir(), 'growerhub-connector-test-'));
const connectors = [];
const clients = [];
const brokers = new Set();
const delay = (ms) => new Promise((done) => setTimeout(done, ms));
async function waitFor(predicate, label) {
  const deadline = Date.now() + 8000;
  while (Date.now() < deadline) { if (predicate()) return; await delay(20); }
  throw new Error(`Timeout: ${label}`);
}
function certificate(name) {
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
    '-subj', '/CN=127.0.0.1', '-addext', 'subjectAltName=IP:127.0.0.1',
    '-keyout', join(directory, `${name}.key`), '-out', join(directory, `${name}.crt`)], { stdio: 'ignore' });
}
function passwordFile(name, users) {
  users.forEach((user, index) => execFileSync('mosquitto_passwd', [...(index === 0 ? ['-c'] : []), '-b',
    join(directory, `${name}.passwd`), user, 'test-password'], { stdio: 'ignore' }));
}
async function broker(name) {
  let output = '';
  const child = spawn('mosquitto', ['-c', join(directory, `${name}.conf`)]);
  brokers.add(child);
  child.stdout.on('data', (data) => { output += data; });
  child.stderr.on('data', (data) => { output += data; });
  await waitFor(() => output.includes('running'), `${name} broker start`);
  return child;
}
async function stopBroker(child) {
  if (child.exitCode !== null || child.signalCode !== null) return;
  const closed = new Promise((done) => child.once('exit', done));
  child.kill('SIGTERM');
  await closed;
  brokers.delete(child);
}
function client(options, topics, messages) {
  const result = mqtt.connect({ protocolVersion: 5, clean: true, resubscribe: false, queueQoSZero: false,
    reconnectPeriod: 100, username: 'observer', password: 'test-password', ...options });
  clients.push(result);
  result.on('error', () => {});
  result.on('connect', () => result.subscribe(topics, { qos: 0 }, (error) => { if (error) throw error; }));
  result.on('message', (topic, data, packet) => messages.push({ topic, payload: data.toString(), retained: packet.retain }));
  return result;
}
const publish = (client, topic, payload, retain = false) => new Promise((done, fail) =>
  client.publish(topic, typeof payload === 'string' ? payload : JSON.stringify(payload), { qos: 1, retain },
    (error) => error ? fail(error) : done()));
const logs = [];
function configuration(username, cloudOverrides = {}) {
  return { v: 1, local: { host: '127.0.0.1', port: 18831, base_topic: 'greenhouse/z2m', username: 'local', password: 'test-password' },
    cloud: { host: '127.0.0.1', port: 28831, username, password: 'test-password', client_id: username,
      base_topic: `gh/z2m/${username}`, ca_file: join(directory, 'server.crt'), ...cloudOverrides },
    limits: { reconnect_ms: 100, connect_timeout_ms: 1000, keepalive_seconds: 1 } };
}
function connector(username, overrides) {
  const result = startConnector(configuration(username, overrides), { log: (code) => logs.push({ username, code }) });
  connectors.push(result);
  return result;
}
const readyCount = () => logs.filter((entry) => entry.code === 'ready').length;
const stateEnvelopes = (messages, name) => messages.filter((entry) => entry.topic.endsWith(`/bridge/relay/${name}`))
  .map((entry) => ({ ...entry, envelope: JSON.parse(entry.payload) }));

try {
  certificate('server');
  certificate('untrusted');
  passwordFile('local', ['local', 'observer']);
  passwordFile('cloud', ['relay-a', 'relay-b', 'observer']);
  writeFileSync(join(directory, 'cloud.acl'), 'user relay-a\ntopic readwrite gh/z2m/relay-a/#\nuser relay-b\ntopic readwrite gh/z2m/relay-b/#\nuser observer\ntopic readwrite #\n');
  for (const name of ['local', 'cloud']) {
    const tls = name === 'cloud' ? `certfile ${directory}/server.crt\nkeyfile ${directory}/server.key\nacl_file ${directory}/cloud.acl\n` : '';
    writeFileSync(join(directory, `${name}.conf`), `user root\nlistener ${name === 'local' ? 18831 : 28831} 127.0.0.1\nallow_anonymous false\npassword_file ${directory}/${name}.passwd\npersistence true\npersistence_location ${directory}/\npersistence_file ${name}.db\nlog_dest stdout\n${tls}`);
  }
  assert(execFileSync('mosquitto', ['-h'], { encoding: 'utf8' }).includes('2.0.22'));
  let localBroker = await broker('local');
  let cloudBroker = await broker('cloud');
  const sourceMessages = [];
  const cloudMessages = [];
  const source = client({ protocol: 'mqtt', host: '127.0.0.1', port: 18831 }, ['greenhouse/z2m/#'], sourceMessages);
  const observer = client({ protocol: 'mqtts', host: '127.0.0.1', port: 28831,
    ca: execFileSync('cat', [join(directory, 'server.crt')]), rejectUnauthorized: true }, ['gh/z2m/#'], cloudMessages);
  await waitFor(() => source.connected && observer.connected, 'fixture clients');
  const names = ['flat', 'garden/nested', 'теплица/почва'];
  const inventory = names.map((friendly_name, index) => ({ friendly_name, ieee_address: `0xsame${index}`, type: 'EndDevice' }));
  await publish(source, 'greenhouse/z2m/bridge/devices', inventory, true);
  await publish(source, 'greenhouse/z2m/bridge/state', { state: 'online' }, true);
  await publish(source, 'greenhouse/z2m/bridge/info', { version: '2.12.0', coordinator: { type: 'zstack',
    meta: { revision: 1, network_key: 'NETWORK_SECRET' } }, config: { mqtt: { password: 'LOCAL_SECRET' } } }, true);
  for (const name of names) await publish(source, `greenhouse/z2m/${name}`, { temperature: 18 }, true);
  const first = connector('relay-a');
  const second = connector('relay-b');
  await waitFor(() => readyCount() >= 2 && stateEnvelopes(cloudMessages, 'теплица/почва').length >= 2, 'two connectors with Unicode snapshots');
  for (const username of ['relay-a', 'relay-b']) {
    for (const name of names) {
      const cached = cloudMessages.find((entry) => entry.topic === `gh/z2m/${username}/bridge/relay/${name}`);
      assert(cached);
      assert.equal(JSON.parse(cached.payload).retained, true);
    }
  }
  assert(!JSON.stringify(cloudMessages).includes('LOCAL_SECRET'));
  assert(!JSON.stringify(cloudMessages).includes('NETWORK_SECRET'));
  const liveStart = cloudMessages.length;
  await publish(source, 'greenhouse/z2m/garden/nested', { temperature: 24 }, true);
  await waitFor(() => stateEnvelopes(cloudMessages.slice(liveStart), 'garden/nested').length === 2, 'fresh state');
  assert(stateEnvelopes(cloudMessages.slice(liveStart), 'garden/nested').every((entry) => !entry.retained && !entry.envelope.retained));
  const commandCount = (topic) => sourceMessages.filter((entry) => entry.topic === `greenhouse/z2m/${topic}`).length;
  const availabilityStart = cloudMessages.length;
  await publish(source, 'greenhouse/z2m/garden/nested/availability', { state: 'online' }, true);
  await waitFor(() => stateEnvelopes(cloudMessages.slice(availabilityStart), 'garden/nested/availability').length === 2, 'nested availability');
  await publish(observer, 'gh/z2m/relay-a/garden/nested/set', { state: 'ON' });
  await waitFor(() => commandCount('garden/nested/set') === 1, 'one hierarchical command');
  await publish(observer, 'gh/z2m/relay-a/garden/nested/set', { state: 'OFF' }, true);
  await publish(observer, 'gh/z2m/other/garden/nested/set', { state: 'ON' });
  await publish(observer, 'gh/z2m/relay-a/unknown/set', { state: 'ON' });
  await publish(source, 'greenhouse/z2m/unknown', { temperature: 99 });
  await publish(source, 'greenhouse/z2m/garden/nested/set', { state: 'ON' });
  await publish(source, 'greenhouse/z2m/bridge/log', 'PRIVATE_LOG');
  await delay(150);
  assert.equal(commandCount('garden/nested/set'), 2);
  assert(!cloudMessages.some((entry) => entry.topic.includes('/bridge/relay/garden/nested/set') || entry.payload.includes('PRIVATE_LOG')));
  assert(!cloudMessages.some((entry) => entry.topic.endsWith('/bridge/relay/unknown')));
  const renamed = [{ friendly_name: 'renamed/soil', ieee_address: '0xsame1' }, { friendly_name: 'flat', ieee_address: '0xsame0' },
    { friendly_name: 'added/sensor', ieee_address: '0xnew' }];
  const beforeRename = readyCount();
  await publish(source, 'greenhouse/z2m/bridge/devices', renamed, true);
  await waitFor(() => readyCount() >= beforeRename + 2, 'renamed routes');
  const addedStart = cloudMessages.length;
  await publish(source, 'greenhouse/z2m/added/sensor', { temperature: 27 }, true);
  await waitFor(() => stateEnvelopes(cloudMessages.slice(addedStart), 'added/sensor').length === 2, 'added device without config change');
  await publish(observer, 'gh/z2m/relay-a/garden/nested/set', { state: 'ON' });
  await publish(observer, 'gh/z2m/relay-a/renamed/soil/set', { state: 'ON' });
  await waitFor(() => commandCount('renamed/soil/set') === 1, 'renamed command');
  await publish(observer, 'gh/z2m/relay-a/renamed/soil/set', { state: 'OFF' }, true);
  await delay(100);
  assert.equal(commandCount('garden/nested/set'), 2);
  const beforeAmbiguity = readyCount();
  await publish(source, 'greenhouse/z2m/bridge/devices', [...renamed,
    { friendly_name: 'ambiguous' }, { friendly_name: 'ambiguous/availability' }], true);
  await waitFor(() => readyCount() >= beforeAmbiguity + 2, 'ambiguous inventory');
  await publish(observer, 'gh/z2m/relay-a/ambiguous/set', { state: 'ON' });
  await delay(100);
  assert.equal(commandCount('ambiguous/set'), 0);
  assert(logs.some((entry) => entry.code === 'ambiguous_names:2'));

  const beforeLocalBreak = readyCount();
  await stopBroker(localBroker);
  await waitFor(() => !first.local.connected && !second.local.connected, 'local disconnect');
  await publish(observer, 'gh/z2m/relay-a/renamed/soil/set', { state: 'OFF' });
  localBroker = await broker('local');
  await waitFor(() => readyCount() >= beforeLocalBreak + 2 && source.connected, 'local reconnect');
  await delay(200);
  assert.equal(commandCount('renamed/soil/set'), 1);
  await publish(observer, 'gh/z2m/relay-a/renamed/soil/set', { state: 'OFF' });
  await waitFor(() => commandCount('renamed/soil/set') === 2, 'fresh command after local reconnect');

  const beforeCloudBreak = readyCount();
  await stopBroker(cloudBroker);
  await waitFor(() => !first.cloud.connected && !second.cloud.connected, 'cloud disconnect');
  await publish(source, 'greenhouse/z2m/renamed/soil', { temperature: 39 }, true);
  await publish(source, 'greenhouse/z2m/renamed/soil', { temperature: 40 }, false);
  const restoreStart = cloudMessages.length;
  cloudBroker = await broker('cloud');
  await waitFor(() => observer.connected && readyCount() >= beforeCloudBreak + 2
    && stateEnvelopes(cloudMessages.slice(restoreStart), 'renamed/soil').length >= 2, 'cloud reconnect snapshots');
  const restored = stateEnvelopes(cloudMessages.slice(restoreStart), 'renamed/soil');
  assert(restored.every((entry) => entry.retained || entry.envelope.retained));
  assert(restored.every((entry) => !entry.envelope.payload.includes('40')));
  const actualStart = cloudMessages.length;
  await publish(source, 'greenhouse/z2m/renamed/soil', { temperature: 43 }, true);
  await waitFor(() => stateEnvelopes(cloudMessages.slice(actualStart), 'renamed/soil').length === 2, 'fresh measurement after cloud reconnect');
  assert(stateEnvelopes(cloudMessages.slice(actualStart), 'renamed/soil').every((entry) => !entry.retained && !entry.envelope.retained));
  assert.equal(commandCount('renamed/soil/set'), 2);

  const deniedTls = connector('tls-denied', { ca_file: join(directory, 'untrusted.crt') });
  const deniedAuth = connector('auth-denied');
  await waitFor(() => logs.some((entry) => entry.username === 'tls-denied' && entry.code === 'cloud_connection_failed')
    && logs.some((entry) => entry.username === 'auth-denied' && entry.code === 'cloud_connection_failed'), 'TLS and authentication refusal');
  assert(!deniedTls.cloud.connected);
  assert(!deniedAuth.cloud.connected);
  assert(!logs.some((entry) => ['tls-denied', 'auth-denied'].includes(entry.username) && entry.code === 'ready'));
  console.log(JSON.stringify({ result: 'passed', broker: '2.0.22', node: process.version, network: 'none',
    checks: ['flat_nested_unicode', 'metadata_secrets', 'namespace_isolation', 'add_rename_remove_ambiguity',
      'cached_and_live_measurements', 'retained_command_rejected_live', 'one_command_no_echo',
      'local_and_cloud_disconnect_no_command_queue', 'no_live_state_replay', 'tls_verification', 'authentication'] }));
} finally {
  await Promise.all(connectors.map((item) => item.stop()));
  await Promise.all(clients.map((item) => new Promise((done) => item.end(true, done))));
  for (const child of brokers) await stopBroker(child);
  assert(directory.startsWith(join(tmpdir(), 'growerhub-connector-test-')));
  rmSync(directory, { recursive: true, force: true });
}
