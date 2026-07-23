import { useEffect, useState } from 'react';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import {
  archiveCoordinator,
  createCoordinator,
  fetchCoordinators,
  rotateCoordinatorCredentials,
} from '../../api/selfService';
import { trackProductGoal } from '../../utils/metrika';
import './SelfServicePages.css';
import { getIntlLocale, translateApp } from '../../locales/i18n';

const STATUS_LABELS = {
  PROVISIONING: translateApp("Настраивается"),
  OFFLINE: translateApp("Не в сети"),
  ONLINE: translateApp("В сети"),
  ERROR: translateApp("Ошибка"),
  ARCHIVED: translateApp("В архиве"),
};

const downloadTextFile = (name, content) => {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/plain;charset=utf-8' }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  anchor.click();
  URL.revokeObjectURL(url);
};

function AppConnections() {
  const [coordinators, setCoordinators] = useState([]);
  const [name, setName] = useState(translateApp("Дополнительный координатор"));
  const [setup, setSetup] = useState(null);
  const [busy, setBusy] = useState('loading');
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setCoordinators(await fetchCoordinators());
      setError('');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = async (event) => {
    event.preventDefault();
    setBusy('create');
    try {
      const result = await createCoordinator(name.trim());
      setSetup({ coordinatorId: result.coordinator.id, ...result.setup });
      trackProductGoal('coordinator_created', { placement: 'connections', step: 'credentials_shown' });
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
      setSetup({ coordinatorId: coordinator.id, ...nextSetup });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  const handleArchive = async (coordinator) => {
    if (!window.confirm(translateApp("Архивировать «{{value1}}» и отозвать его доступ к MQTT?", { value1: coordinator.name }))) return;
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
        <section className="secret-card">
          <div><span>{translateApp("Одноразовые данные")}</span><h2>{translateApp("Сохраните конфигурацию сейчас")}</h2><p>{translateApp("После закрытия страницы пароль восстановить нельзя — только выпустить новый.")}</p></div>
          <dl><div><dt>{translateApp("Имя пользователя")}</dt><dd>{setup.username}</dd></div><div><dt>{translateApp("Пароль")}</dt><dd>{setup.password}</dd></div><div><dt>{translateApp("Базовый топик")}</dt><dd>{setup.base_topic}</dd></div></dl>
          <div className="inline-actions"><Button variant="primary" onClick={() => downloadTextFile('configuration.yaml', setup.configuration_yaml)}>configuration.yaml</Button><Button onClick={() => downloadTextFile('secret.yaml', setup.secret_yaml)}>secret.yaml</Button><Button onClick={() => setSetup(null)}>{translateApp("Скрыть навсегда")}</Button></div>
        </section>
      ) : null}

      <section className="self-service-section">
        <h2>{translateApp("Ваши координаторы")}</h2>
        <div className="connection-list">
          {coordinators.map((coordinator) => (
            <article key={coordinator.id}>
              <div><h3>{coordinator.name}</h3><p>{coordinator.base_topic}</p></div>
              <span className={coordinator.status === 'ONLINE' ? 'status-chip is-online' : 'status-chip'}>{STATUS_LABELS[coordinator.status] || translateApp("Статус неизвестен")}</span>
              <div className="connection-meta"><span>{translateApp('device_count', { count: coordinator.device_count })}</span><span>{coordinator.last_seen_at ? translateApp("Связь: {{value1}}", { value1: new Date(coordinator.last_seen_at).toLocaleString(getIntlLocale()) }) : translateApp("Ещё не подключался")}</span></div>
              <div className="inline-actions"><Button onClick={() => handleRotate(coordinator)} isLoading={busy === coordinator.id}>{translateApp("Новые данные доступа")}</Button><Button variant="danger" onClick={() => handleArchive(coordinator)} disabled={busy === coordinator.id}>{translateApp("Архивировать")}</Button></div>
            </article>
          ))}
          {coordinators.length === 0 ? <AppPageState kind="empty" title={translateApp("Координаторов пока нет")} /> : null}
        </div>
      </section>

      <section className="self-service-section">
        <h2>{translateApp("Добавить координатор")}</h2>
        <p>{translateApp("В одном пространстве можно использовать несколько координаторов и подключать оборудование в удобном темпе.")}</p>
        <form className="compact-form" onSubmit={handleCreate}><label>{translateApp("Название")}<input value={name} onChange={(event) => setName(event.target.value)} required maxLength="120" /></label><Button type="submit" variant="primary" isLoading={busy === 'create'}>{translateApp("Создать")}</Button></form>
      </section>
    </div>
  );
}

export default AppConnections;
