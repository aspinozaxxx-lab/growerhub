import { useEffect, useState } from 'react';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import PushokConnect from '../../components/PushokConnect';
import PushokPilot from '../../components/PushokPilot';
import { pushokError } from '../../utils/pushokConnection';
import NetworkCoordinatorPilot from '../../components/NetworkCoordinatorPilot';
import CoordinatorSetup, { CoordinatorConnectionMode } from '../../components/CoordinatorSetup';
import {
  archiveCoordinator,
  createCoordinator,
  fetchCoordinators,
  rotateCoordinatorCredentials,
} from '../../api/selfService';
import { trackProductGoal } from '../../utils/analytics';
import { CONNECTION_MODES } from '../../domain/coordinatorSetup';
import './SelfServicePages.css';
import { translateApp } from '../../locales/i18n';
import { formatDateTimeDDMMYYYY } from '../../utils/formatters';

const STATUS_LABELS = {
  PROVISIONING: translateApp("Настраивается"),
  OFFLINE: translateApp("Не в сети"),
  ONLINE: translateApp("В сети"),
  ERROR: translateApp("Ошибка"),
  ARCHIVED: translateApp("В архиве"),
};

function AppConnections() {
  const [coordinators, setCoordinators] = useState([]);
  const [name, setName] = useState(translateApp("Дополнительный координатор"));
  const [setup, setSetup] = useState(null);
  const [connectionMode, setConnectionMode] = useState(null);
  const [busy, setBusy] = useState('loading');
  const [error, setError] = useState('');

  const load = async (background = false) => {
    try {
      setCoordinators(await fetchCoordinators());
      if (!background) setError('');
    } catch (requestError) {
      if (!background) setError(requestError.message);
    } finally {
      if (!background) setBusy('');
    }
  };

  useEffect(() => { load(); const timer = window.setInterval(() => load(true), 5000); return () => window.clearInterval(timer); }, []);

  const handleCreate = async (event) => {
    event.preventDefault();
    if (!Object.values(CONNECTION_MODES).includes(connectionMode) || setup) return;
    setBusy('create');
    try {
      const result = await createCoordinator(name.trim());
      setSetup({ coordinatorId: result.coordinator.id, ...result.setup });
      trackProductGoal('coordinator_created', { placement: 'connections', step: 'credentials_shown', connection_mode: connectionMode });
      await load();
    } catch (requestError) {
      setError(requestError.message);
      setBusy('');
    }
  };

  const handleRotate = async (coordinator) => {
    if (!window.confirm(translateApp("Отозвать старый MQTT-пароль подключения «{{value1}}»?", { value1: coordinator.name }))) return;
    setBusy(coordinator.id);
    try {
      const nextSetup = await rotateCoordinatorCredentials(coordinator.id);
      setConnectionMode(null);
      setSetup({ coordinatorId: coordinator.id, ...nextSetup });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  const handleArchive = async (coordinator) => {
    if (!window.confirm(coordinator.transport === 'PUSHOK_CLOUD'
      ? translateApp('Отключить ПушОк от GrowerHub? Доступ в Управляторе сохранится.')
      : translateApp("Архивировать «{{value1}}» и отозвать его доступ к MQTT?", { value1: coordinator.name }))) return;
    setBusy(coordinator.id);
    try {
      await archiveCoordinator(coordinator.id);
      if (setup?.coordinatorId === coordinator.id) setSetup(null);
      await load();
    } catch (requestError) {
      setError(requestError.message);
      setBusy('');
    }
  };

  if (busy === 'loading') return <AppPageState kind="loading" title={translateApp("Загружаем подключения…")} />;

  return (
    <div className="self-service-page">
      <AppPageHeader title={translateApp("Подключения")} />
      {error ? <AppPageState kind="error" title={error} /> : null}

      {setup ? (
        <CoordinatorSetup
          key={setup.coordinatorId}
          setup={setup}
          connectionMode={connectionMode}
          onConnectionModeChange={setConnectionMode}
          onHide={() => { setSetup(null); setConnectionMode(null); }}
        />
      ) : null}

      <section className="self-service-section">
        <h2>{translateApp("Ваши координаторы")}</h2>
        <div className="connection-list">
          {coordinators.map((coordinator) => (
            <article key={coordinator.id}>
              <div><h3>{coordinator.name}</h3><p className="ym-hide-content">{coordinator.transport === 'PUSHOK_CLOUD' ? coordinator.hub_id : coordinator.base_topic}</p>
                {coordinator.connection_error ? <p role="alert">{pushokError(coordinator.connection_error)}</p> : null}</div>
              <span className={coordinator.status === 'ONLINE' ? 'status-chip is-online' : 'status-chip'}>{STATUS_LABELS[coordinator.status] || translateApp("Статус неизвестен")}</span>
              <div className="connection-meta"><span>{translateApp('device_count', { count: coordinator.device_count })}</span><span>{coordinator.last_seen_at ? translateApp("Связь: {{value1}}", { value1: formatDateTimeDDMMYYYY(coordinator.last_seen_at) }) : translateApp("Ещё не подключался")}</span></div>
              <div className="inline-actions">{coordinator.transport === 'PUSHOK_CLOUD'
                ? <PushokConnect coordinator={coordinator} onCreated={() => load()} />
                : <Button onClick={() => handleRotate(coordinator)} isLoading={busy === coordinator.id}>{translateApp("Новые данные доступа")}</Button>}
                <Button variant="danger" onClick={() => handleArchive(coordinator)} disabled={busy === coordinator.id}>{translateApp("Архивировать")}</Button></div>
            </article>
          ))}
          {coordinators.length === 0 ? <AppPageState kind="empty" title={translateApp("Координаторов пока нет")} /> : null}
        </div>
      </section>

      <section className="self-service-section">
        <h2>{translateApp("Добавить координатор")}</h2>
        <PushokConnect onCreated={() => load()} />
        <PushokPilot support />
        <div className="inline-actions"><NetworkCoordinatorPilot placement="connections_zs_eht_pilot" /></div>
        <p>{translateApp("В одном пространстве можно использовать несколько координаторов и подключать оборудование в удобном темпе.")}</p>
        {!setup ? <>
          <CoordinatorConnectionMode value={connectionMode} onChange={setConnectionMode} />
          {connectionMode ? <form className="compact-form" onSubmit={handleCreate}><label>{translateApp("Название")}<input value={name} onChange={(event) => setName(event.target.value)} required maxLength="120" /></label><Button type="submit" variant="primary" isLoading={busy === 'create'}>{translateApp("Создать")}</Button></form> : null}
        </> : null}
      </section>
    </div>
  );
}

export default AppConnections;
