import { useEffect, useState } from 'react';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import { fetchAutomationOverview, replaceSectionScenarios } from '../../api/selfService';
import { trackProductGoal } from '../../utils/metrika';
import './SelfServicePages.css';

const LABELS = {
  BOX_CLIMATE: 'Климатический порог',
  LIGHT_SCHEDULE: 'Расписание освещения',
  WATERING: 'Полив (beta)',
};

const toRequest = (scenarios, changedType, patch) => scenarios.map((scenario) => ({
  scenario_type: scenario.scenario_type,
  enabled: scenario.scenario_type === changedType ? patch.enabled : scenario.enabled,
  config: scenario.scenario_type === changedType ? { ...scenario.config, ...patch.config } : scenario.config,
}));

function AppAutomations() {
  const [overview, setOverview] = useState(null);
  const [busy, setBusy] = useState('loading');
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setOverview(await fetchAutomationOverview());
      setError('');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  useEffect(() => { load(); }, []);

  const saveScenario = async (section, scenario, patch) => {
    if (scenario.scenario_type === 'WATERING' && patch.enabled && !window.confirm('Полив — beta. Проверьте воду, питание, аварийное отключение и безопасный лимит времени. Включить?')) return;
    const key = `${section.id}:${scenario.scenario_type}`;
    setBusy(key);
    try {
      const updated = await replaceSectionScenarios(section.id, toRequest(section.scenarios, scenario.scenario_type, patch));
      setOverview(updated);
      if (patch.enabled) trackProductGoal('automation_enabled', { placement: 'automations', scenario_type: scenario.scenario_type });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  if (busy === 'loading') return <AppPageState kind="loading" title="Загружаем автоматизации…" />;
  const sections = (overview?.rooms || []).flatMap((zone) => zone.boxes.map((section) => ({ ...section, zoneName: zone.name })));

  return (
    <div className="self-service-page">
      <AppPageHeader title="Автоматизации" />
      {error ? <AppPageState kind="error" title={error} /> : null}
      <p className="page-intro">Автоматизации необязательны. Начните с мониторинга и ручного управления, затем включайте готовые сценарии по одному.</p>
      {sections.length === 0 ? <AppPageState kind="empty" title="Сначала создайте зону" /> : null}
      <div className="automation-list">
        {sections.map((section) => (
          <section className="self-service-section" key={section.id}>
            <div className="section-heading"><div><h2>{section.zoneName} · {section.name}</h2><p>Сценарии этой зоны</p></div></div>
            {(section.scenarios || []).filter((scenario) => LABELS[scenario.scenario_type]).map((scenario) => {
              const readiness = scenario.readiness || section.readiness?.[scenario.scenario_type];
              const key = `${section.id}:${scenario.scenario_type}`;
              return (
                <article className="automation-card" key={scenario.scenario_type}>
                  <div><h3>{LABELS[scenario.scenario_type]}</h3><p>{readiness?.ready ? 'Оборудование готово' : readiness?.reason || 'Проверьте назначенные устройства'}</p></div>
                  {scenario.scenario_type === 'LIGHT_SCHEDULE' ? <div className="scenario-fields"><label>Включить<input type="time" defaultValue={scenario.config?.start_time || '06:00'} onBlur={(event) => saveScenario(section, scenario, { enabled: scenario.enabled, config: { start_time: event.target.value } })} /></label><label>Выключить<input type="time" defaultValue={scenario.config?.end_time || '22:00'} onBlur={(event) => saveScenario(section, scenario, { enabled: scenario.enabled, config: { end_time: event.target.value } })} /></label></div> : null}
                  {scenario.scenario_type === 'BOX_CLIMATE' ? <div className="scenario-fields"><label>Ниже, °C<input type="number" step="0.5" defaultValue={scenario.config?.min_c ?? 24} onBlur={(event) => saveScenario(section, scenario, { enabled: scenario.enabled, config: { min_c: Number(event.target.value) } })} /></label><label>Выше, °C<input type="number" step="0.5" defaultValue={scenario.config?.max_c ?? 28} onBlur={(event) => saveScenario(section, scenario, { enabled: scenario.enabled, config: { max_c: Number(event.target.value) } })} /></label></div> : null}
                  {scenario.scenario_type === 'WATERING' ? <p className="beta-warning">Сценарий доступен только при назначенных датчике влажности и насосе. Используются интервалы и ограничения длительности; физическое аварийное отключение остаётся обязательным.</p> : null}
                  <Button variant={scenario.enabled ? 'danger' : 'primary'} disabled={!scenario.enabled && !readiness?.ready} isLoading={busy === key} onClick={() => saveScenario(section, scenario, { enabled: !scenario.enabled, config: {} })}>{scenario.enabled ? 'Выключить' : 'Включить'}</Button>
                </article>
              );
            })}
          </section>
        ))}
      </div>
    </div>
  );
}

export default AppAutomations;
