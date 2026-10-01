import { describe, expect, it } from 'vitest';
import { buildConnectorConfig, isLocalMqttValid } from './coordinatorSetup';

describe('coordinatorSetup', () => {
  const setup = { server: 'ssl://growerhub.ru:8883', username: 'z2m_demo', password: 'one-time-secret',
    client_id: 'z2m_demo', base_topic: 'gh/z2m/z2m_demo' };
  const local = { host: '192.0.2.10', port: '1883', username: 'local', password: 'local-secret' };

  it('builds structured config with issued identity and local-only credentials', () => {
    const config = JSON.parse(buildConnectorConfig({ setup, local }));
    expect(config.v).toBe(1);
    expect(config.cloud).toEqual({ host: 'growerhub.ru', port: 8883, username: setup.username,
      password: setup.password, client_id: setup.client_id, base_topic: setup.base_topic });
    expect(config.local).toEqual({ ...local, port: 1883, base_topic: 'zigbee2mqtt' });
    expect(config.cloud).not.toHaveProperty('local');
  });

  it('trims the address and topic while preserving passwords and safely escaping JSON', () => {
    const connection = { host: ' 192.0.2.10 ', port: ' 1883 ', username: 'local', password: ' "secret\\value" ', baseTopic: ' greenhouse/z2m/ ' };
    expect(isLocalMqttValid(connection)).toBe(true);
    const config = JSON.parse(buildConnectorConfig({ setup, local: connection }));
    expect(config.local.host).toBe('192.0.2.10');
    expect(config.local.port).toBe(1883);
    expect(config.local.base_topic).toBe('greenhouse/z2m');
    expect(config.local.password).toBe(connection.password);
  });

  it('keeps separate stable client IDs for connectors sharing a broker', () => {
    const first = JSON.parse(buildConnectorConfig({ setup, local }));
    const secondSetup = { ...setup, username: 'z2m_second', client_id: 'z2m_second', base_topic: 'gh/z2m/z2m_second' };
    const second = JSON.parse(buildConnectorConfig({ setup: secondSetup, local }));
    expect(first.cloud.client_id).not.toBe(second.cloud.client_id);
    expect(JSON.parse(buildConnectorConfig({ setup: { ...setup, password: 'rotated' }, local })).cloud.client_id)
      .toBe(first.cloud.client_id);
  });

  it('uses the issued endpoint and IPv6 local host', () => {
    const config = JSON.parse(buildConnectorConfig({ setup: { ...setup, server: 'mqtts://example.test:9993' },
      local: { host: '[2001:db8::1]', port: '1883' } }));
    expect(config.cloud.host).toBe('example.test');
    expect(config.cloud.port).toBe(9993);
    expect(config.local.host).toBe('[2001:db8::1]');
    expect(config.local).not.toHaveProperty('username');
  });

  it.each([
    { host: '', port: '1883' }, { host: '192.0.2.10', port: '65536' },
    { host: '192.0.2.10', port: '1883', baseTopic: 'z2m/#' },
    { host: '192.0.2.10', port: '1883', password: 'secret\nlistener 1883' },
  ])('does not generate an invalid local connection', (connection) => {
    expect(buildConnectorConfig({ setup, local: connection })).toBe('');
  });

  it.each([
    { client_id: '' }, { base_topic: 'gh/z2m/other' }, { server: 'mqtt://example.test:1883' },
    { server: 'mqtts://user:pass@example.test:8883' }, { server: 'mqtts://example.test:8883/path' },
  ])('does not guess identity or permit plaintext cloud traffic', (changes) => {
    expect(buildConnectorConfig({ setup: { ...setup, ...changes }, local })).toBe('');
  });
});
