import { describe, expect, it } from 'vitest';
import {
  buildBridgeConfig,
  buildSectionResources,
  encodeFeatureChoice,
  getReadableFeatures,
  getWritableSwitches,
} from './onboardingModel';

const overview = {
  devices: [{
    ieee_address: 'synthetic-device',
    friendly_name: 'Датчик 1',
    metrics: [{ property: 'temperature', label: 'Температура' }],
    controls: [{ property: 'state', value_on: 'ON', value_off: 'OFF' }],
  }],
};

describe('onboardingModel', () => {
  it('выбирает только читаемую температуру и writable state', () => {
    expect(getReadableFeatures(overview, 'temperature')).toHaveLength(1);
    expect(getWritableSwitches(overview)).toHaveLength(1);
  });

  it('создаёт tenant-aware bindings без лишних идентификаторов', () => {
    const choice = encodeFeatureChoice(overview.devices[0], { property: 'temperature' });
    const switchChoice = encodeFeatureChoice(overview.devices[0], { property: 'state' });
    const resources = buildSectionResources({
      coordinatorId: 'coordinator-public-id',
      temperatureChoice: choice,
      lightChoice: switchChoice,
      overview,
    });

    expect(resources).toEqual([
      expect.objectContaining({ role: 'AIR_TEMPERATURE_SENSOR', zigbee_coordinator_id: 'coordinator-public-id' }),
      expect.objectContaining({ role: 'LIGHT_SWITCH', command_property: 'state' }),
    ]);
  });

  it('создаёт направленный bridge без wildcard-forward', () => {
    const setup = {
      username: 'z2m_demo',
      password: 'one-time-secret',
      client_id: 'z2m_demo',
      base_topic: 'gh/z2m/z2m_demo',
    };
    const config = buildBridgeConfig({
      setup,
      local: { host: '192.0.2.10', port: '1883', username: 'local', password: 'local-secret' },
    });

    expect(config).toContain('topic +/set in');
    expect(config).toContain('topic bridge/state out');
    expect(config).toContain('# Создано в браузере GrowerHub.');
    expect(config).not.toContain('Sozdan v brauzere');
    expect(config).not.toContain('topic #');

    const englishConfig = buildBridgeConfig({
      setup,
      local: {
        host: '192.0.2.10',
        port: '1883',
        username: 'local',
        password: 'local-secret',
      },
      locale: 'en',
    });
    expect(englishConfig).toContain('# Generated in the GrowerHub browser interface.');
    expect(englishConfig).not.toMatch(/[\u0400-\u04ff]/u);
  });
});

describe('podklyuchenie sushchestvujushchego MQTT', () => {
  const setup = { username: 'u', password: 'p', client_id: 'c', base_topic: 'gh/z2m/u' };
  it('ne vytesnjaet drugoj connector na tom zhe lokalnom brokere', () => {
    const local = { host: '192.0.2.10', port: '1883' };
    const first = buildBridgeConfig({ setup, local });
    const second = buildBridgeConfig({
      setup: { username: 'u2', password: 'p2', client_id: 'c2', base_topic: 'gh/z2m/u2' },
      local,
    });
    const clientIds = (config) => [...config.matchAll(/^remote_clientid (.+)$/gmu)].map((match) => match[1]);

    expect(new Set([...clientIds(first), ...clientIds(second)]).size).toBe(4);
    expect(clientIds(first)[1]).toBe(setup.client_id);
    expect(clientIds(buildBridgeConfig({ setup: { ...setup, password: 'rotated' }, local })))
      .toEqual(clientIds(first));
    expect(buildBridgeConfig({ setup: { ...setup, client_id: '' }, local })).toBe('');
  });

  it('napravljaet sostojanija i komandy v vybrannuju bazovuju temu', () => {
    const config = buildBridgeConfig({ setup, local: { host: '192.0.2.10', port: '1883', baseTopic: 'greenhouse/z2m' } });
    expect(config).toContain('topic + in 1 relay/from-local/ greenhouse/z2m/');
    expect(config).toContain('topic +/set out 1 relay/to-local/ greenhouse/z2m/');
    expect(config).not.toContain(' zigbee2mqtt/');
    expect(config).not.toContain('topic #');
  });
  it.each([
    { host: '', port: '1883' },
    { host: '192.0.2.10', port: '65536' },
    { host: '192.0.2.10', port: '1883', baseTopic: 'z2m/#' },
    { host: '192.0.2.10', port: '1883', password: 'secret\nlistener 1883' },
  ])('ne sozdaet nekorrektnyj ili dopolnennyj direktivami konfiguracionnyj fajl', (local) => {
    expect(buildBridgeConfig({ setup, local })).toBe('');
  });
});
