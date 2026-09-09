import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchFarmsOverview, replaceGreenhouseScenarios, setScenariosEnabled } from '../../api/selfService';
import { createScenarioDrafts, listOrEmpty, overviewFarms } from '../farm/farmModel';
import { translateApp as t } from '../../locales/i18n';
import { trackProductGoal } from '../../utils/analytics';

const wateringConfirmation = () => window.confirm(t(
  'Перед включением полива проверьте подачу воды, питание, аварийное отключение и безопасный лимит времени. Включить сценарий?',
));

export default function useAutomations() {
  const [overview, setOverview] = useState(null);
  const [drafts, setDrafts] = useState({});
  const [busy, setBusy] = useState('loading');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const locked = useRef(false);
  const farms = overviewFarms(overview);
  const greenhouses = farms.flatMap((farm) => listOrEmpty(farm.greenhouses)
    .map((greenhouse) => ({ ...greenhouse, farmName: farm.name, farmEnabled: farm.enabled })));

  const load = useCallback(async () => {
    setBusy('loading');
    setError('');
    try {
      setOverview(await fetchFarmsOverview());
    } catch (requestError) {
      setError(requestError?.message || t('Не удалось загрузить автоматизации'));
    } finally {
      setBusy('');
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const keyFor = (greenhouse, type) => `${greenhouse.id}:${type}`;
  const scenarioFor = (greenhouse, type) => createScenarioDrafts(greenhouse)[type];
  const configFor = (greenhouse, type) => ({
    ...scenarioFor(greenhouse, type).config,
    ...drafts[keyFor(greenhouse, type)],
  });
  const isDirty = (greenhouse, type) => {
    const saved = scenarioFor(greenhouse, type).config;
    return Object.entries(drafts[keyFor(greenhouse, type)] || {})
      .some(([field, value]) => value !== saved[field]);
  };
  const patchConfig = (greenhouse, type, patch) => {
    if (locked.current) return;
    const key = keyFor(greenhouse, type);
    setDrafts((current) => ({ ...current, [key]: { ...current[key], ...patch } }));
    setNotice('');
  };

  const run = async (key, action, onSuccess) => {
    if (locked.current) return;
    locked.current = true;
    setBusy(key);
    setError('');
    setNotice('');
    try {
      const payload = await action();
      setOverview(payload);
      onSuccess?.(payload);
    } catch (requestError) {
      setError(requestError?.message || t('Не удалось сохранить сценарий'));
    } finally {
      locked.current = false;
      setBusy('');
    }
  };

  const saveSettings = (greenhouse, type) => {
    const key = keyFor(greenhouse, type);
    const scenario = scenarioFor(greenhouse, type);
    return run(key, () => replaceGreenhouseScenarios(greenhouse.id, [{
      ...scenario, config: configFor(greenhouse, type),
    }]), () => {
      setDrafts((current) => {
        const next = { ...current };
        delete next[key];
        return next;
      });
      setNotice(t('Настройки сохранены'));
    });
  };

  const toggleScenario = (greenhouse, type) => {
    const scenario = scenarioFor(greenhouse, type);
    if (!scenario.enabled && type === 'WATERING' && !wateringConfirmation()) return;
    return run(keyFor(greenhouse, type), () => replaceGreenhouseScenarios(greenhouse.id, [{
      ...scenario, enabled: !scenario.enabled,
    }]), () => {
      if (!scenario.enabled) trackProductGoal('automation_enabled', { placement: 'automations', scenario_type: type });
    });
  };

  const toggleAll = (enabled) => {
    const startsWatering = enabled && greenhouses.some((greenhouse) => (
      greenhouse.enabled && greenhouse.farmEnabled
      && !scenarioFor(greenhouse, 'WATERING').enabled
      && (greenhouse.readiness?.WATERING || greenhouse.scenarios?.find((s) => s.scenario_type === 'WATERING')?.readiness)?.ready
    ));
    if (startsWatering && !wateringConfirmation()) return;
    return run('all', () => setScenariosEnabled(enabled), () => {
      setNotice(enabled ? t('Готовые сценарии включены') : t('Автоматизации выключены. Настройки сохранены.'));
    });
  };

  return {
    overview, farms, greenhouses, busy, error, notice, load,
    scenarioFor, configFor, isDirty, patchConfig, saveSettings, toggleScenario, toggleAll,
  };
}
