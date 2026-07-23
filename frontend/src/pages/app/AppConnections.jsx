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

const STATUS_LABELS = {
  PROVISIONING: 'Настраивается',
  OFFLINE: 'Не в сети',
  ONLINE: 'В сети',
  ERROR: 'Ошибка',
  ARCHIVED: 'В архиве',
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
  const [name, setName] = useState('Дополнительный координатор');
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
    if (!window.confirm(`Отозвать старый MQTT-пароль подключения «${coordinator.name}»?`)) return;
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
    if (!window.confirm(`Архивировать «${coordinator.name}» и отозвать его доступ к MQTT?`)) return;
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

  if (busy === 'loading') return <AppPageState kind="loading" title="Загружаем подключения…" />;

  return (
    <div className="self-service-page">
      <AppPageHeader title="Подключения" />
      {error ? <AppPageState kind="error" title={error} /> : null}

      {setup ? (
        <section className="secret-card">
          <div><span>Одноразовые данные</span><h2>Сохраните конфигурацию сейчас</h2><p>После закрытия страницы пароль восстановить нельзя — только выпустить новый.</p></div>
          <dl><div><dt>Имя пользователя</dt><dd>{setup.username}</dd></div><div><dt>Пароль</dt><dd>{setup.password}</dd></div><div><dt>Базовый топик</dt><dd>{setup.base_topic}</dd></div></dl>
          <div className="inline-actions"><Button variant="primary" onClick={() => downloadTextFile('configuration.yaml', setup.configuration_yaml)}>configuration.yaml</Button><Button onClick={() => downloadTextFile('secret.yaml', setup.secret_yaml)}>secret.yaml</Button><Button onClick={() => setSetup(null)}>Скрыть навсегда</Button></div>
        </section>
      ) : null}

      <section className="self-service-section">
        <h2>Ваши координаторы</h2>
        <div className="connection-list">
          {coordinators.map((coordinator) => (
            <article key={coordinator.id}>
              <div><h3>{coordinator.name}</h3><p>{coordinator.base_topic}</p></div>
              <span className={coordinator.status === 'ONLINE' ? 'status-chip is-online' : 'status-chip'}>{STATUS_LABELS[coordinator.status] || 'Статус неизвестен'}</span>
              <div className="connection-meta"><span>{coordinator.device_count} устройств</span><span>{coordinator.last_seen_at ? `Связь: ${new Date(coordinator.last_seen_at).toLocaleString('ru-RU')}` : 'Ещё не подключался'}</span></div>
              <div className="inline-actions"><Button onClick={() => handleRotate(coordinator)} isLoading={busy === coordinator.id}>Новые данные доступа</Button><Button variant="danger" onClick={() => handleArchive(coordinator)} disabled={busy === coordinator.id}>Архивировать</Button></div>
            </article>
          ))}
          {coordinators.length === 0 ? <AppPageState kind="empty" title="Координаторов пока нет" /> : null}
        </div>
      </section>

      <section className="self-service-section">
        <h2>Добавить координатор</h2>
        <p>В одном пространстве можно использовать несколько координаторов и подключать оборудование в удобном темпе.</p>
        <form className="compact-form" onSubmit={handleCreate}><label>Название<input value={name} onChange={(event) => setName(event.target.value)} required maxLength="120" /></label><Button type="submit" variant="primary" isLoading={busy === 'create'}>Создать</Button></form>
      </section>
    </div>
  );
}

export default AppConnections;
