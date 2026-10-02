import { translateApp as t } from '../../locales/i18n';
import Button from '../../components/ui/Button';

export default function WateringScenarioFields({ config, onChange, disabled }) {
  const mode = config.trigger_mode || 'legacy';
  const number = (name, label, min = 1, max) => <label>{t(label)}<input type="number" required min={min} max={max} step="any"
    value={config[name] ?? ''} onChange={(e) => onChange({ [name]: e.target.value === '' ? '' : Number(e.target.value) })} /></label>;
  return <fieldset className="automations-watering-fields" disabled={disabled}>
    <label className="watering-field-wide">{t('Когда поливать')}<select value={mode} onChange={(e) => onChange({
      trigger_mode: e.target.value === 'legacy' ? undefined : e.target.value,
      ...(e.target.value !== 'legacy' ? { observe_only: true, stop_mode: 'fixed_duration' } : {}),
    })}>
      <option value="legacy">{t('Прежнее правило')}</option>
      <option value="schedule">{t('По расписанию')}</option>
      <option value="drying">{t('По местному ориентиру высыхания')}</option>
      <option value="adaptive">{t('По прогнозу высыхания')}</option>
    </select></label>
    {mode === 'legacy' ? <>
      {number('soil_threshold_percent', 'Порог почвы, %', 0, 100)}
      {number('max_interval_hours', 'Максимальная пауза, ч', 0)}
    </> : <>
      <label className="watering-field-wide">{t('Работа сценария')}<select value={config.observe_only === false ? 'control' : 'observe'}
        onChange={(e) => onChange({ observe_only: e.target.value !== 'control' })}>
        <option value="observe">{t('Наблюдать без команд')}</option>
        <option value="control">{t('Управлять поливом')}</option>
      </select></label>
      {mode === 'schedule' ? <>
        <label>{t('Время полива')}<input type="time" required value={config.schedule_time ?? '07:00'}
          onChange={(e) => onChange({ schedule_time: e.target.value })} /></label>
        <div className="watering-weekdays watering-field-wide" role="group" aria-label={t('Дни полива')}>
          {['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'].map((day, i) => <label key={day}>
            <input type="checkbox" checked={(config.schedule_days ?? [1, 2, 3, 4, 5, 6, 7]).includes(i + 1)}
              onChange={(e) => onChange({ schedule_days: e.target.checked
                ? [...(config.schedule_days ?? [1, 2, 3, 4, 5, 6, 7]), i + 1].filter((v, n, a) => a.indexOf(v) === n)
                : (config.schedule_days ?? [1, 2, 3, 4, 5, 6, 7]).filter((v) => v !== i + 1) })} />{t(day)}</label>)}
        </div>
        <p className="watering-field-wide">{t('Датчик не обязателен. Время — по часовому поясу профиля; пропущенный полив не догоняется.')}</p>
      </> : <>
        <label>{t('Начало окна')}<input type="time" required value={config.window_start ?? '07:00'} onChange={(e) => onChange({ window_start: e.target.value })} /></label>
        <label>{t('Конец окна')}<input type="time" required value={config.window_end ?? '10:00'} onChange={(e) => onChange({ window_end: e.target.value })} /></label>
        <label>{t('После обычного полива')}<input type="number" step="any" value={config.wet_anchor ?? ''}
          onChange={(e) => onChange({ wet_anchor: e.target.value === '' ? undefined : Number(e.target.value) })} /></label>
        <label>{t('Перед обычным поливом')}<input type="number" step="any" value={config.dry_anchor ?? ''}
          onChange={(e) => onChange({ dry_anchor: e.target.value === '' ? undefined : Number(e.target.value) })} /></label>
        <p className="watering-field-wide">{t('Это показания вашего датчика в двух проверенных состояниях почвы. Процент доступной воды из них не вычисляется. После перестановки датчика или смены грунта подтвердите ориентиры заново.')}</p>
        <Button className="watering-field-wide" type="button" size="sm" disabled={!Number.isFinite(config.wet_anchor) || !Number.isFinite(config.dry_anchor)}
          onClick={() => onChange({ calibration_request: crypto.randomUUID() })}>
          {t(config.calibration_request ? 'Подтверждение будет сохранено' : 'Подтвердить ориентиры для этого положения')}
        </Button>
      </>}
    </>}
    {number('run_seconds', 'Длительность, сек')}
    {number('min_interval_hours', 'Минимальная пауза, ч', mode === 'legacy' ? 0 : 1)}
    {number('daily_max_seconds', 'Лимит в сутки, сек')}
    {mode !== 'legacy' ? <>
      <label className="watering-checkbox watering-field-wide"><input type="checkbox" checked={Boolean(config.pulse_enabled)}
        onChange={(e) => onChange({ pulse_enabled: e.target.checked })} />{t('Подавать воду импульсами')}</label>
      {config.pulse_enabled ? <>{number('pulse_run_minutes', 'Импульс, мин', 0.01)}{number('pulse_pause_minutes', 'Пауза, мин', 0.01)}</> : null}
      <p className="watering-field-wide">{t('Длительность — суммарное время подачи воды. Паузы в неё не входят. Возможности и безопасный предел исполнителя проверяет сервер.')}</p>
    </> : null}
  </fieldset>;
}
