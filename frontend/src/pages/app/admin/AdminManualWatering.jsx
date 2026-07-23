import React, { useMemo, useState } from 'react';
import { AlertTriangle, Clock3, Droplets, History, Pause, Play, Square, Waves } from 'lucide-react';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import Button from '../../../components/ui/Button';
import Modal from '../../../components/ui/Modal';
import Surface from '../../../components/ui/Surface';
import useAdminManualWatering from '../../../features/manual-watering/useAdminManualWatering';
import {
  completionReasonLabel,
  formatDateTime,
  formatDurationSeconds,
  formatVolumeLiters,
  listOrEmpty,
  modeAvailable,
  modeLabel,
  phaseLabel,
  pumpCanStart,
  pumpCanStop,
  pumpCurrentSession,
  pumpStartBlockReasons,
  sessionBoxesLabel,
  sessionPlantsCount,
  sourceLabel,
  startBlockReasonLabel,
  wateringDefaultsReady,
} from '../../../features/manual-watering/manualWateringModel';
import './AdminManualWatering.css';

function pumpTitle(pump) {
  const base = pump?.label || `Насос ${pump?.id ?? ''}`.trim();
  return pump?.channel === null || pump?.channel === undefined
    ? base
    : `${base} · канал ${pump.channel}`;
}

function SessionSummary({ session }) {
  return (
    <article className="manual-watering-session">
      <div className="manual-watering-session__heading">
        <strong>{formatDateTime(session.started_at)}</strong>
        <span>{sourceLabel(session.source)}</span>
      </div>
      <div className="manual-watering-session__facts">
        <span>{modeLabel(session.mode)}</span>
        <span>{formatDurationSeconds(session.active_duration_s)}</span>
        <span>
          {formatVolumeLiters(session.known_volume_l)}
          {session.partial_volume && session.known_volume_l !== null && session.known_volume_l !== undefined ? ' · частично' : ''}
        </span>
      </div>
      <div className="manual-watering-session__scope">
        {sessionBoxesLabel(session)} · {sessionPlantsCount(session)} растений
      </div>
      <div className={`manual-watering-session__reason is-${session.completion_reason || 'unknown'}`}>
        {session.finished_at
          ? completionReasonLabel(session.completion_reason)
          : phaseLabel(session.phase)}
      </div>
      {session.error_message ? (
        <div className="manual-watering-session__error">{session.error_message}</div>
      ) : null}
    </article>
  );
}

function LeakSensorState({ sensor }) {
  const tone = sensor.triggered ? 'danger' : sensor.available ? 'success' : 'muted';
  const label = sensor.triggered ? 'Протечка' : sensor.available ? 'Сухо' : 'Нет данных';
  return (
    <span className={`manual-watering-leak is-${tone}`}>
      <Waves size={14} aria-hidden="true" />
      <span>{sensor.label || 'Датчик протечки'}: {label}</span>
    </span>
  );
}

function WateringBox({ box }) {
  const plants = listOrEmpty(box.plants);
  const leakSensors = listOrEmpty(box.leak_sensors);
  return (
    <section className={`manual-watering-box ${box.enabled === false ? 'is-disabled' : ''}`}>
      <header className="manual-watering-box__header">
        <div>
          <h4>{box.name || 'Бокс без названия'}</h4>
          <span>{box.room_name || 'Помещение не указано'}</span>
        </div>
        {box.enabled === false ? <span className="manual-watering-box__disabled">Выключен в автоматизации</span> : null}
      </header>
      <div className="manual-watering-box__plants">
        {plants.length === 0 ? (
          <span className="manual-watering-empty">Растения не привязаны</span>
        ) : plants.map((plant) => (
          <div className="manual-watering-plant" key={plant.id ?? plant.plant_id}>
            <span>{plant.name || plant.plant_name || 'Растение без названия'}</span>
            <small>
              {plant.rate_ml_per_hour === null || plant.rate_ml_per_hour === undefined
                ? 'Скорость не указана'
                : `${plant.rate_ml_per_hour} мл/ч`}
            </small>
          </div>
        ))}
      </div>
      <div className="manual-watering-box__leaks">
        {leakSensors.length === 0 ? (
          <span className="manual-watering-empty">Датчик протечки не привязан</span>
        ) : leakSensors.map((sensor) => (
          <LeakSensorState key={sensor.reference || sensor.resource_binding_id || sensor.external_id} sensor={sensor} />
        ))}
      </div>
    </section>
  );
}

function ActiveSession({ pump, session, actionKey, onStop }) {
  const stopping = actionKey === `stop:${pump.id}`;
  return (
    <section className="manual-watering-active" aria-live="polite">
      <div className="manual-watering-active__icon" aria-hidden="true">
        {session.phase === 'pause' ? <Pause size={22} /> : <Droplets size={22} />}
      </div>
      <div className="manual-watering-active__body">
        <strong>{phaseLabel(session.phase)}</strong>
        <span>{modeLabel(session.mode)} · {sourceLabel(session.source)}</span>
        <div className="manual-watering-active__facts">
          <span>Активно: {formatDurationSeconds(session.active_duration_s)}</span>
          {session.remaining_active_s !== null && session.remaining_active_s !== undefined ? (
            <span>Осталось: {formatDurationSeconds(session.remaining_active_s)}</span>
          ) : null}
          {session.pulse_enabled ? (
            <span>Импульс: {formatDurationSeconds(session.pulse_run_s)} / пауза {formatDurationSeconds(session.pulse_pause_s)}</span>
          ) : null}
          {session.pulse_enabled && session.phase_remaining_s !== null && session.phase_remaining_s !== undefined ? (
            <span>До смены фазы: {formatDurationSeconds(session.phase_remaining_s)}</span>
          ) : null}
        </div>
      </div>
      <Button
        type="button"
        variant="danger"
        size="sm"
        onClick={() => onStop(pump.id)}
        disabled={!pumpCanStop(pump) || Boolean(actionKey)}
        isLoading={stopping}
      >
        <Square size={14} aria-hidden="true" />
        Остановить все боксы
      </Button>
    </section>
  );
}

function PumpHistory({ pumpId, history, onLoadMore }) {
  if (!history?.loaded && history?.isLoading) {
    return <div className="manual-watering-state">Загрузка журнала...</div>;
  }
  if (history?.error) {
    return <div className="manual-watering-state is-error">{history.error}</div>;
  }
  const items = listOrEmpty(history?.items);
  return (
    <div className="manual-watering-history">
      {items.length === 0 ? (
        <div className="manual-watering-state">Завершённых сессий пока нет</div>
      ) : items.map((session) => <SessionSummary key={session.id} session={session} />)}
      {history?.nextBeforeId ? (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => onLoadMore(pumpId)}
          disabled={history.isLoading}
          isLoading={history.isLoading}
        >
          Показать ещё
        </Button>
      ) : null}
    </div>
  );
}

function PumpCard({
  pump,
  actionKey,
  history,
  historyOpen,
  startConfigReady,
  onHistoryToggle,
  onStart,
  onStop,
  onLoadMore,
}) {
  const session = pumpCurrentSession(pump);
  const blockedReasons = pumpStartBlockReasons(pump);
  const startDisabled = !pumpCanStart(pump) || !startConfigReady || Boolean(actionKey);
  return (
    <Surface variant="card" padding="md" className="manual-watering-pump">
      <header className="manual-watering-pump__header">
        <div>
          <h3>{pumpTitle(pump)}</h3>
          <span>{pump.device_key || pump.device_id || 'Устройство не указано'}</span>
        </div>
        <div className="manual-watering-pump__statuses">
          <span className={pump.is_online === true ? 'is-online' : pump.is_online === false ? 'is-offline' : ''}>
            {pump.is_online === true ? 'В сети' : pump.is_online === false ? 'Не в сети' : 'Связь неизвестна'}
          </span>
          <span className={pump.is_running === true ? 'is-running' : ''}>
            {pump.is_running === true ? 'Насос включён' : pump.is_running === false ? 'Ожидание' : 'Состояние неизвестно'}
          </span>
        </div>
      </header>

      {session ? <ActiveSession pump={pump} session={session} actionKey={actionKey} onStop={onStop} /> : null}

      <div className="manual-watering-pump__boxes">
        {listOrEmpty(pump.boxes).length === 0 ? (
          <div className="manual-watering-state">Боксы не привязаны. Настройте иерархию в разделе «Автоматизация».</div>
        ) : listOrEmpty(pump.boxes).map((box) => <WateringBox key={box.id} box={box} />)}
      </div>

      <footer className="manual-watering-pump__footer">
        <div className="manual-watering-pump__start">
          <Button type="button" variant="primary" onClick={() => onStart(pump)} disabled={startDisabled}>
            <Play size={15} aria-hidden="true" />
            Начать полив
          </Button>
          {!pumpCanStart(pump) && blockedReasons.length > 0 ? (
            <span className="manual-watering-block-reason">
              <AlertTriangle size={14} aria-hidden="true" />
              {blockedReasons.map(startBlockReasonLabel).join(' · ')}
            </span>
          ) : !startConfigReady ? (
            <span className="manual-watering-block-reason">
              <AlertTriangle size={14} aria-hidden="true" />
              Сервер не передал параметры полива по умолчанию
            </span>
          ) : null}
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => onHistoryToggle(pump.id)}
          aria-expanded={historyOpen}
          aria-controls={`pump-history-${pump.id}`}
        >
          <History size={15} aria-hidden="true" />
          {historyOpen ? 'Скрыть журнал' : 'Журнал насоса'}
        </Button>
      </footer>

      {historyOpen ? (
        <section id={`pump-history-${pump.id}`} aria-label={`Журнал: ${pumpTitle(pump)}`}>
          <PumpHistory pumpId={pump.id} history={history} onLoadMore={onLoadMore} />
        </section>
      ) : null}
    </Surface>
  );
}

function NumberField({ label, value, onChange, disabled = false }) {
  return (
    <label className="manual-watering-form__field">
      <span>{label}</span>
      <input
        type="number"
        min="1"
        step="1"
        inputMode="numeric"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled}
        required={!disabled}
      />
    </label>
  );
}

function LaunchWateringModal({ pump, defaults, actionKey, actionError, onClose, onStart }) {
  const initialMode = modeAvailable(pump, 'timed') ? 'timed' : 'until_leak';
  const [form, setForm] = useState({
    mode: initialMode,
    duration_minutes: String(defaults.timed_duration_s / 60),
    max_active_duration_minutes: String(defaults.until_leak_max_active_duration_s / 60),
    pulse_enabled: false,
    pulse_run_minutes: String(defaults.pulse_run_s / 60),
    pulse_pause_minutes: String(defaults.pulse_pause_s / 60),
  });
  const [formError, setFormError] = useState('');
  const isStarting = actionKey === `start:${pump.id}`;

  const setField = (field, value) => setForm((prev) => ({ ...prev, [field]: value }));
  const submit = async (event) => {
    event.preventDefault();
    const numericFields = [
      form.mode === 'timed' ? 'duration_minutes' : 'max_active_duration_minutes',
      ...(form.pulse_enabled ? ['pulse_run_minutes', 'pulse_pause_minutes'] : []),
    ];
    if (numericFields.some((field) => !Number.isInteger(Number(form[field])) || Number(form[field]) <= 0)) {
      setFormError('Все интервалы должны быть целыми положительными числами');
      return;
    }
    setFormError('');
    const completed = await onStart(pump.id, {
      mode: form.mode,
      ...(form.mode === 'timed'
        ? { duration_s: Number(form.duration_minutes) * 60 }
        : { max_active_duration_s: Number(form.max_active_duration_minutes) * 60 }),
      pulse_enabled: form.pulse_enabled,
      ...(form.pulse_enabled ? {
        pulse_run_s: Number(form.pulse_run_minutes) * 60,
        pulse_pause_s: Number(form.pulse_pause_minutes) * 60,
      } : {}),
    });
    if (completed) onClose();
  };

  const footer = (
    <div className="modal__actions">
      <Button type="button" variant="secondary" onClick={onClose} disabled={isStarting}>Отмена</Button>
      <Button type="submit" variant="primary" form={`manual-watering-form-${pump.id}`} isLoading={isStarting}>
        Запустить
      </Button>
    </div>
  );

  return (
    <Modal
      isOpen
      title={`Запуск: ${pumpTitle(pump)}`}
      onClose={onClose}
      footer={footer}
      disableOverlayClose={isStarting}
    >
      <form id={`manual-watering-form-${pump.id}`} className="manual-watering-form" onSubmit={submit}>
        <div className="manual-watering-form__warning">
          <Droplets size={18} aria-hidden="true" />
          <span>Будут поливаться все привязанные боксы, включая выключенные в автоматизации.</span>
        </div>
        <fieldset className="manual-watering-form__modes">
          <legend>Режим полива</legend>
          <label>
            <input
              type="radio"
              name="watering-mode"
              value="timed"
              checked={form.mode === 'timed'}
              onChange={(event) => setField('mode', event.target.value)}
              disabled={!modeAvailable(pump, 'timed')}
            />
            <span>По времени</span>
          </label>
          <label>
            <input
              type="radio"
              name="watering-mode"
              value="until_leak"
              checked={form.mode === 'until_leak'}
              onChange={(event) => setField('mode', event.target.value)}
              disabled={!modeAvailable(pump, 'until_leak')}
            />
            <span>До протечки</span>
          </label>
          {!modeAvailable(pump, 'until_leak') ? (
            <small>Режим доступен только при наличии рабочего датчика протечки.</small>
          ) : null}
        </fieldset>

        {form.mode === 'timed' ? (
          <NumberField label="Общее активное время, мин" value={form.duration_minutes} onChange={(value) => setField('duration_minutes', value)} />
        ) : (
          <NumberField label="Предельное активное время, мин" value={form.max_active_duration_minutes} onChange={(value) => setField('max_active_duration_minutes', value)} />
        )}

        <label className="manual-watering-form__pulse-toggle">
          <input
            type="checkbox"
            checked={form.pulse_enabled}
            onChange={(event) => setField('pulse_enabled', event.target.checked)}
          />
          <span>Импульсный режим</span>
        </label>
        {form.pulse_enabled ? (
          <div className="manual-watering-form__pulse-fields">
            <NumberField label="Работа насоса, мин" value={form.pulse_run_minutes} onChange={(value) => setField('pulse_run_minutes', value)} />
            <NumberField label="Пауза, мин" value={form.pulse_pause_minutes} onChange={(value) => setField('pulse_pause_minutes', value)} />
          </div>
        ) : null}
        {formError || actionError ? (
          <div className="manual-watering-form__error" role="alert">{formError || actionError}</div>
        ) : null}
      </form>
    </Modal>
  );
}

function AdminManualWatering() {
  const {
    overview,
    isLoading,
    error,
    actionError,
    notice,
    actionKey,
    histories,
    loadSessions,
    startWatering,
    stopWatering,
    clearActionError,
  } = useAdminManualWatering();
  const [launchPump, setLaunchPump] = useState(null);
  const [openHistories, setOpenHistories] = useState({});
  const pumps = useMemo(() => listOrEmpty(overview?.pumps), [overview]);
  const startConfigReady = wateringDefaultsReady(overview?.defaults);

  const toggleHistory = async (pumpId) => {
    const nextOpen = !openHistories[pumpId];
    setOpenHistories((prev) => ({ ...prev, [pumpId]: nextOpen }));
    if (nextOpen && !histories[pumpId]?.loaded) {
      await loadSessions(pumpId);
    }
  };
  const openLaunchModal = (pump) => {
    clearActionError();
    setLaunchPump(pump);
  };
  const closeLaunchModal = () => {
    clearActionError();
    setLaunchPump(null);
  };

  return (
    <div className="admin-page manual-watering-page">
      <AppPageHeader
        title="Ручной полив"
        subtitle="Насосы, привязанные боксы и единый журнал сессий"
        right={(
          <span className="manual-watering-page__polling">
            <Clock3 size={14} aria-hidden="true" />
            Обновляется автоматически
          </span>
        )}
      />

      {isLoading && !overview ? <AppPageState kind="loading" title="Загрузка ручного полива..." /> : null}
      {error ? <AppPageState kind="error" title={error} /> : null}
      {actionError && !launchPump ? <div className="admin-error" role="alert">{actionError}</div> : null}
      {notice ? <div className="admin-notice" role="status">{notice}</div> : null}
      {!isLoading && !error && pumps.length === 0 ? (
        <AppPageState
          kind="empty"
          title="Насосы для ручного полива не настроены"
          hint="Привяжите насосы, боксы и растения в разделе автоматизации."
        />
      ) : null}

      <div className="manual-watering-pumps">
        {pumps.map((pump) => (
          <PumpCard
            key={pump.id}
            pump={pump}
            actionKey={actionKey}
            history={histories[pump.id]}
            historyOpen={Boolean(openHistories[pump.id])}
            startConfigReady={startConfigReady}
            onHistoryToggle={toggleHistory}
            onStart={openLaunchModal}
            onStop={stopWatering}
            onLoadMore={(pumpId) => loadSessions(pumpId, { append: true })}
          />
        ))}
      </div>

      {launchPump ? (
        <LaunchWateringModal
          key={launchPump.id}
          pump={launchPump}
          defaults={overview.defaults}
          actionKey={actionKey}
          actionError={actionError}
          onClose={closeLaunchModal}
          onStart={startWatering}
        />
      ) : null}
    </div>
  );
}

export default AdminManualWatering;
