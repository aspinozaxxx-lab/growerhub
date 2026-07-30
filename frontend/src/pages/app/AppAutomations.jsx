import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import {
  fetchFarmsOverview,
  replaceGreenhouseScenarios,
} from '../../api/selfService';
import {
  SCENARIO_LABELS,
  createScenarioDrafts,
  listOrEmpty,
  overviewFarms,
} from '../../features/farm/farmModel';
import { ClimateScenarioFields } from '../../features/farm/ClimateScenarioFields';
import { trackProductGoal } from '../../utils/analytics';
import { translateApp } from '../../locales/i18n';
import './SelfServicePages.css';

const toRequest = (scenarios, changedType, patch) => scenarios.map((scenario) => ({
  scenario_type: scenario.scenario_type,
  enabled: scenario.scenario_type === changedType
    ? Boolean(patch.enabled ?? scenario.enabled)
    : Boolean(scenario.enabled),
  config: scenario.scenario_type === changedType
    ? { ...(scenario.config || {}), ...(patch.config || {}) }
    : (scenario.config || {}),
}));

function AppAutomations() {
  const [overview, setOverview] = useState(null);
  const [greenhouseDrafts, setGreenhouseDrafts] = useState({});
  const [busy, setBusy] = useState('loading');
  const [error, setError] = useState('');

  const applyOverview = useCallback((payload) => {
    const normalized = payload && typeof payload === 'object' ? payload : {};
    const farms = overviewFarms(normalized);
    setOverview(normalized);
    setGreenhouseDrafts(Object.fromEntries(farms.flatMap((farm) => (
      listOrEmpty(farm.greenhouses).map((greenhouse) => [
        greenhouse.id,
        createScenarioDrafts(greenhouse),
      ])
    ))));
  }, []);

  const load = useCallback(async () => {
    try {
      applyOverview(await fetchFarmsOverview());
      setError('');
    } catch (requestError) {
      setError(requestError?.message || translateApp("Не удалось загрузить автоматизации"));
    } finally {
      setBusy('');
    }
  }, [applyOverview]);

  useEffect(() => {
    load();
  }, [load]);

  const saveScenario = async (greenhouse, scenario, patch) => {
    if (
      scenario.scenario_type === 'WATERING'
      && patch.enabled
      && !scenario.enabled
      && !window.confirm(translateApp(
        "Перед включением полива проверьте подачу воды, питание, аварийное отключение и безопасный лимит времени. Включить сценарий?",
      ))
    ) {
      return;
    }
    const key = `${greenhouse.id}:${scenario.scenario_type}`;
    setBusy(key);
    setError('');
    try {
      const payload = await replaceGreenhouseScenarios(
        greenhouse.id,
        toRequest(listOrEmpty(greenhouse.scenarios), scenario.scenario_type, patch),
      );
      applyOverview(payload);
      if (patch.enabled && !scenario.enabled) {
        trackProductGoal('automation_enabled', {
          placement: 'automations',
          scenario_type: scenario.scenario_type,
        });
      }
    } catch (requestError) {
      setError(requestError?.message || translateApp("Не удалось сохранить сценарий"));
    } finally {
      setBusy('');
    }
  };

  const patchGreenhouseScenario = (greenhouseId, scenarioType, field, value) => {
    setGreenhouseDrafts((current) => {
      const scenarios = current[greenhouseId] || {};
      const scenario = scenarios[scenarioType] || {
        scenario_type: scenarioType,
        enabled: false,
        config: {},
      };
      return {
        ...current,
        [greenhouseId]: {
          ...scenarios,
          [scenarioType]: {
            ...scenario,
            config: {
              ...(scenario.config || {}),
              [field]: value,
            },
          },
        },
      };
    });
  };

  const saveGreenhouseSettings = async (greenhouse, scenarioType) => {
    const scenario = greenhouseDrafts[greenhouse.id]?.[scenarioType];
    if (!scenario) return;
    await saveScenario(greenhouse, scenario, {
      enabled: scenario.enabled,
      config: scenario.config,
    });
  };

  if (busy === 'loading') {
    return <AppPageState kind="loading" title={translateApp("Загружаем автоматизации…")} />;
  }

  const farms = overviewFarms(overview);
  const greenhouses = farms.flatMap((farm) => listOrEmpty(farm.greenhouses)
    .map((greenhouse) => ({ ...greenhouse, farmName: farm.name })));
  return (
    <div className="self-service-page">
      <AppPageHeader
        title={translateApp("Автоматизации")}
        right={<Link className="gh-btn gh-btn--secondary gh-btn--md" to="/app/farm/">{translateApp("Настроить слоты")}</Link>}
      />
      <p className="page-intro">
        {translateApp("Сценарии становятся доступны после заполнения обязательных слотов в Конструкторе фермы.")}
      </p>
      {error ? <AppPageState kind="error" title={error} /> : null}
      {farms.length === 0 ? (
        <AppPageState kind="empty" title={translateApp("Сначала создайте ферму")} />
      ) : null}
      {farms.length > 0 && greenhouses.length === 0 ? (
        <AppPageState kind="empty" title={translateApp("Сначала добавьте теплицу")} />
      ) : null}
      <div className="automation-list">
        {greenhouses.map((greenhouse) => (
          <section className="self-service-section" key={greenhouse.id}>
            <div className="section-heading">
              <div>
                <h2>{greenhouse.name}</h2>
                <p>{greenhouse.farmName} · {translateApp("Сценарии этой теплицы")}</p>
              </div>
              <span className={greenhouse.enabled ? 'status-chip is-online' : 'status-chip'}>
                {greenhouse.enabled ? translateApp("Активна") : translateApp("Выключена")}
              </span>
            </div>
            {listOrEmpty(greenhouse.scenarios).map((scenario) => {
              const scenarioDraft = greenhouseDrafts[greenhouse.id]?.[scenario.scenario_type]
                || scenario;
              const readiness = greenhouse.readiness?.[scenario.scenario_type] || scenario.readiness;
              const blocked = !readiness?.ready && !scenario.enabled;
              const key = `${greenhouse.id}:${scenario.scenario_type}`;
              return (
                <article className="automation-card" key={scenario.scenario_type}>
                  <div>
                    <h3>{translateApp(SCENARIO_LABELS[scenario.scenario_type] || scenario.scenario_type)}</h3>
                    <p className={readiness?.ready ? 'automation-readiness is-ready' : 'automation-readiness'}>
                      {readiness?.ready ? translateApp("Готово к запуску") : readiness?.reason}
                    </p>
                  </div>
                  <Button
                    variant={scenario.enabled ? 'danger' : 'primary'}
                    onClick={() => saveScenario(greenhouse, scenario, { enabled: !scenario.enabled })}
                    isLoading={busy === key}
                    disabled={blocked}
                  >
                    {scenario.enabled ? translateApp("Выключить") : translateApp("Включить")}
                  </Button>
                  {scenario.scenario_type === 'LIGHT_SCHEDULE' ? (
                    <div className="scenario-fields">
                      <label>
                        {translateApp("Включить")}
                        <input
                          type="time"
                          defaultValue={scenario.config?.start_time || '06:00'}
                          onBlur={(event) => saveScenario(greenhouse, scenario, {
                            config: { start_time: event.target.value },
                          })}
                        />
                      </label>
                      <label>
                        {translateApp("Выключить")}
                        <input
                          type="time"
                          defaultValue={scenario.config?.end_time || '22:00'}
                          onBlur={(event) => saveScenario(greenhouse, scenario, {
                            config: { end_time: event.target.value },
                          })}
                        />
                      </label>
                    </div>
                  ) : null}
                  {scenario.scenario_type === 'BOX_CLIMATE' ? (
                    <>
                      <ClimateScenarioFields
                        config={scenarioDraft.config}
                        onChange={(field, value) => patchGreenhouseScenario(
                          greenhouse.id,
                          scenario.scenario_type,
                          field,
                          value,
                        )}
                      />
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => saveGreenhouseSettings(greenhouse, scenario.scenario_type)}
                        isLoading={busy === key}
                      >
                        {translateApp('Сохранить настройки')}
                      </Button>
                    </>
                  ) : null}
                  {scenario.scenario_type === 'WATERING' ? (
                    <p className="automation-note">
                      {translateApp("Полив использует датчик влажности почвы, насос и ограничения длительности.")}
                    </p>
                  ) : null}
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
