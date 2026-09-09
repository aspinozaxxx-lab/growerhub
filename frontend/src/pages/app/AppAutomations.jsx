import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRight, Check, Clock3, Droplets, Info, Moon, Power, Sun, Thermometer } from 'lucide-react';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import { ClimateScenarioFields } from '../../features/farm/ClimateScenarioFields';
import { FARM_SCENARIO_TYPES, SCENARIO_LABELS, listOrEmpty } from '../../features/farm/farmModel';
import LightScheduleRange from '../../features/automation/LightScheduleRange';
import useAutomations from '../../features/automation/useAutomations';
import { lightDuration, timeToMinutes } from '../../features/automation/lightSchedule';
import { getUiTimeZone } from '../../utils/formatters';
import { translateApp as t } from '../../locales/i18n';
import './AppAutomations.css';

const LIGHT = 'LIGHT_SCHEDULE';
const CLIMATE = 'BOX_CLIMATE';
const WATERING = 'WATERING';
const WATERING_FIELDS = [
  ['soil_threshold_percent', 'Порог почвы, %', 0, 100],
  ['min_interval_hours', 'Минимальная пауза, ч', 0],
  ['max_interval_hours', 'Максимальная пауза, ч', 0],
  ['run_seconds', 'Длительность, сек', 1],
  ['daily_max_seconds', 'Лимит в сутки, сек', 1],
];

function readinessFor(greenhouse, type) {
  if (greenhouse.farmEnabled === false) return { ready: false, reason: t('Ферма выключена') };
  if (greenhouse.enabled === false) return { ready: false, reason: t('Теплица выключена') };
  return greenhouse.readiness?.[type]
    || greenhouse.scenarios?.find((scenario) => scenario.scenario_type === type)?.readiness
    || { ready: false, reason: t('Назначьте необходимые слоты') };
}

function ScenarioSwitch({ greenhouse, type, automation }) {
  const scenario = automation.scenarioFor(greenhouse, type);
  const readiness = readinessFor(greenhouse, type);
  return (
    <button type="button" role="switch" className="automation-switch"
      aria-label={t('{{scenario}}: {{name}}', { scenario: t(SCENARIO_LABELS[type]), name: greenhouse.name })}
      aria-checked={scenario.enabled}
      disabled={Boolean(automation.busy) || (!scenario.enabled && !readiness.ready)}
      title={!readiness.ready ? readiness.reason : (scenario.enabled ? t('Выключить') : t('Включить'))}
      onClick={() => automation.toggleScenario(greenhouse, type)}>
      <span aria-hidden="true" />
    </button>
  );
}

function SettingsState({ greenhouse, type, automation }) {
  const readiness = readinessFor(greenhouse, type);
  const dirty = automation.isDirty(greenhouse, type);
  return (
    <span className={`automation-settings-state ${dirty || !readiness.ready ? 'is-pending' : ''}`}>
      {dirty || !readiness.ready ? <Info size={14} /> : <Check size={14} />}
      {dirty ? t('Не сохранено') : (!readiness.ready ? readiness.reason : t('Настройки сохранены'))}
    </span>
  );
}

const durationLabel = (minutes) => (minutes % 60
  ? t('{{hours}} ч {{minutes}} мин', { hours: Math.floor(minutes / 60), minutes: minutes % 60 })
  : t('{{hours}} ч', { hours: minutes / 60 }));

function AppAutomations() {
  const automation = useAutomations();
  const { overview, farms, greenhouses, busy, error, notice } = automation;
  const [selectedId, setSelectedId] = useState(null);
  const selected = greenhouses.find((greenhouse) => greenhouse.id === selectedId) || greenhouses[0];
  const allScenarios = farms.flatMap((farm) => [
    ...listOrEmpty(farm.scenarios),
    ...listOrEmpty(farm.greenhouses).flatMap((greenhouse) => listOrEmpty(greenhouse.scenarios)),
  ]);
  const anyEnabled = allScenarios.some((scenario) => scenario.enabled);
  const allEnabled = greenhouses.length > 0 && greenhouses.every((greenhouse) => (
    FARM_SCENARIO_TYPES.every((type) => automation.scenarioFor(greenhouse, type).enabled)
  ));
  const anyReady = greenhouses.some((greenhouse) => FARM_SCENARIO_TYPES.some((type) => readinessFor(greenhouse, type).ready));
  const canEnableMore = greenhouses.some((greenhouse) => FARM_SCENARIO_TYPES.some((type) => (
    !automation.scenarioFor(greenhouse, type).enabled && readinessFor(greenhouse, type).ready
  )));
  const light = selected ? automation.configFor(selected, LIGHT) : {};
  const start = timeToMinutes(light.start_time);
  const end = timeToMinutes(light.end_time);
  const duration = start !== null && end !== null ? lightDuration(start, end) : null;

  if (busy === 'loading' && !overview) {
    return <div className="automations-page"><AppPageState kind="loading" title={t('Загружаем автоматизации…')} /></div>;
  }

  return (
    <div className="automations-page">
      <AppPageHeader title={t('Автоматизации')}
        right={<Link className="automations-equipment-link" to="/app/farm/">{t('Оборудование в Конструкторе')}<ArrowUpRight size={17} /></Link>} />
      {error ? <AppPageState kind="error" title={error}>
        {!overview ? <Button onClick={automation.load}>{t('Повторить')}</Button> : null}
      </AppPageState> : null}
      <section className="automations-master" aria-label={t('Все автоматизации')}>
        <span className={`automations-master__icon ${anyEnabled ? 'is-on' : ''}`}><Power size={28} /></span>
        <div className="automations-master__title"><h2>{t('Все автоматизации')}</h2><p>{t('Во всех фермах')}</p></div>
        <span className={`automation-status ${anyEnabled ? 'is-on' : ''}`}>
          <i />{anyEnabled ? (allEnabled ? t('Включены') : t('Частично включены')) : t('Выключены')}
        </span>
        <div className="automations-master__action">
          <div className="automations-master__buttons">
            {anyEnabled && canEnableMore ? (
              <Button variant="primary" onClick={() => automation.toggleAll(true)} disabled={Boolean(busy)}>
                <Power size={18} />{t('Включить всё')}
              </Button>
            ) : null}
            <Button variant={anyEnabled ? 'secondary' : 'primary'} onClick={() => automation.toggleAll(!anyEnabled)}
              disabled={Boolean(busy) || (!anyEnabled && !anyReady)} isLoading={busy === 'all'}>
              <Power size={18} />{anyEnabled ? t('Выключить всё') : t('Включить всё')}
            </Button>
          </div>
          <small>{t('Настройки сохраняются при выключении')}</small>
        </div>
      </section>
      <div className="automations-feedback" role="status" aria-live="polite">{notice}</div>
      {overview && greenhouses.length === 0 ? (
        <AppPageState kind="empty" title={t(farms.length ? 'Сначала добавьте теплицу' : 'Сначала создайте ферму')}>
          <Link to="/app/settings/zones/">{t('Открыть настройки зон')}</Link>
        </AppPageState>
      ) : null}
      {selected ? <>
        <section className="automations-light-panel" aria-label={t('Расписание освещения')}>
          <header className="automations-panel-heading">
            <Sun className="automations-panel-icon" size={27} />
            <div><h2>{t('Освещение')}</h2><p>{t('Расписание всех теплиц')}</p></div>
            <span className="automations-timezone"><Clock3 size={15} />{getUiTimeZone()}</span>
          </header>
          <div className="automations-timeline">
            <div className="automations-timeline__axis" aria-hidden="true">
              <span>{t('Теплица')}</span>
              <div>{['00:00', '06:00', '12:00', '18:00', '24:00'].map((time) => <span key={time}>{time}</span>)}</div>
              <span>{t('Сценарий')}</span>
            </div>
            {greenhouses.map((greenhouse) => {
              const config = automation.configFor(greenhouse, LIGHT);
              const readiness = readinessFor(greenhouse, LIGHT);
              return (
                <div className={`automations-timeline__row ${selected.id === greenhouse.id ? 'is-selected' : ''}`} key={greenhouse.id}>
                  <button type="button" className="automations-timeline__name" onClick={() => setSelectedId(greenhouse.id)}
                    aria-pressed={selected.id === greenhouse.id}>
                    <strong>{greenhouse.name}{automation.isDirty(greenhouse, LIGHT) ? <i aria-label={t('Не сохранено')} /> : null}</strong>
                    <span>{greenhouse.farmName}</span>
                    {!readiness.ready ? <small>{readiness.reason}</small> : null}
                  </button>
                  <div className="automations-timeline__range">
                    <LightScheduleRange name={greenhouse.name} config={config} disabled={Boolean(busy)}
                      onSelect={() => setSelectedId(greenhouse.id)}
                      onChange={(patch) => automation.patchConfig(greenhouse, LIGHT, patch)} />
                    <span className="automations-timeline__mobile-times">{config.start_time} — {config.end_time}</span>
                  </div>
                  <ScenarioSwitch greenhouse={greenhouse} type={LIGHT} automation={automation} />
                </div>
              );
            })}
          </div>
          <form className="automations-light-editor" onSubmit={(event) => { event.preventDefault(); automation.saveSettings(selected, LIGHT); }}>
            <div className="automations-light-editor__title">
              <h3>{selected.name}<span>{t('Настройки освещения')}</span></h3>
              <SettingsState greenhouse={selected} type={LIGHT} automation={automation} />
            </div>
            <label>{t('Начало')}<input type="time" required step="60" value={light.start_time} disabled={Boolean(busy)}
              onChange={(event) => automation.patchConfig(selected, LIGHT, { start_time: event.target.value })} /></label>
            <label>{t('Конец')}<input type="time" required step="60" value={light.end_time} disabled={Boolean(busy)}
              onChange={(event) => automation.patchConfig(selected, LIGHT, { end_time: event.target.value })} /></label>
            <div className="automations-light-editor__duration">
              <span><Sun size={15} />{duration === null ? '—' : t('Свет: {{duration}}', { duration: durationLabel(duration) })}</span>
              <span><Moon size={15} />{duration === null ? '—' : t('Темнота: {{duration}}', { duration: durationLabel(1440 - duration) })}</span>
              {duration === 1440 ? <small>{t('Круглосуточно')}</small> : (start > end ? <small>{t('Через полночь')}</small> : null)}
            </div>
            <Button type="submit" variant="primary" disabled={Boolean(busy) || duration === null || !automation.isDirty(selected, LIGHT)}
              isLoading={busy === `${selected.id}:${LIGHT}`}>{t('Сохранить')}</Button>
            <p className="automations-light-editor__help"><Info size={14} />{t('Края — начало и конец. Середина — перенос интервала.')}</p>
          </form>
        </section>
        <div className="automations-selection">
          <label>{t('Настройки теплицы')}<select value={selected.id} onChange={(event) => setSelectedId(Number(event.target.value))}>
            {greenhouses.map((greenhouse) => <option key={greenhouse.id} value={greenhouse.id}>{greenhouse.farmName} · {greenhouse.name}</option>)}
          </select></label>
          <span>{t('Включаются только готовые сценарии активных теплиц.')}</span>
        </div>
        <div className="automations-details">
          <form className="automations-settings-panel" aria-label={t('Климат: {{name}}', { name: selected.name })}
            onSubmit={(event) => { event.preventDefault(); automation.saveSettings(selected, CLIMATE); }}>
            <header className="automations-panel-heading">
              <Thermometer className="automations-panel-icon" size={25} />
              <div><h2>{t('Климат')}<span> · {selected.name}</span></h2><SettingsState greenhouse={selected} type={CLIMATE} automation={automation} /></div>
              <ScenarioSwitch greenhouse={selected} type={CLIMATE} automation={automation} />
            </header>
            <fieldset disabled={Boolean(busy)}>
              <ClimateScenarioFields config={automation.configFor(selected, CLIMATE)}
                onChange={(field, value) => automation.patchConfig(selected, CLIMATE, { [field]: value })} />
            </fieldset>
            <footer><Button type="submit" variant="secondary" size="sm" disabled={Boolean(busy) || !automation.isDirty(selected, CLIMATE)}
              isLoading={busy === `${selected.id}:${CLIMATE}`}>{t('Сохранить климат')}</Button></footer>
          </form>
          <form className="automations-settings-panel" aria-label={t('Полив: {{name}}', { name: selected.name })}
            onSubmit={(event) => { event.preventDefault(); automation.saveSettings(selected, WATERING); }}>
            <header className="automations-panel-heading">
              <Droplets className="automations-panel-icon" size={25} />
              <div><h2>{t('Полив')}<span> · {selected.name}</span></h2><SettingsState greenhouse={selected} type={WATERING} automation={automation} /></div>
              <ScenarioSwitch greenhouse={selected} type={WATERING} automation={automation} />
            </header>
            <fieldset className="automations-watering-fields" disabled={Boolean(busy)}>
              {WATERING_FIELDS.map(([field, label, min, max]) => <label key={field}>{t(label)}
                <input type="number" required min={min} max={max} step="any" value={automation.configFor(selected, WATERING)[field] ?? ''}
                  onChange={(event) => automation.patchConfig(selected, WATERING, { [field]: event.target.value === '' ? '' : Number(event.target.value) })} />
              </label>)}
            </fieldset>
            <footer><Button type="submit" variant="secondary" size="sm" disabled={Boolean(busy) || !automation.isDirty(selected, WATERING)}
              isLoading={busy === `${selected.id}:${WATERING}`}>{t('Сохранить полив')}</Button></footer>
          </form>
        </div>
      </> : null}
    </div>
  );
}

export default AppAutomations;
