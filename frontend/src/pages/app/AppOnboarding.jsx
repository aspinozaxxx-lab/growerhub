import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import TelegramContactLink from '../../components/TelegramContactLink';
import Button from '../../components/ui/Button';
import { GITHUB_RELEASES_URL } from '../../domain/siteConfig';
import {
  createCoordinator,
  createZone,
  createZoneSection,
  enablePermitJoin,
  fetchCoordinatorOverview,
  fetchCoordinators,
  fetchOnboardingStatus,
  replaceSectionResources,
  rotateCoordinatorCredentials,
} from '../../api/selfService';
import { trackProductGoal, trackProductGoalOnce } from '../../utils/metrika';
import {
  buildBridgeConfig,
  buildSectionResources,
  CONNECTION_MODES,
  encodeFeatureChoice,
  getReadableFeatures,
  getWritableSwitches,
  SETUP_PLATFORMS,
} from './onboardingModel';
import './AppOnboarding.css';

const POLL_INTERVAL_MS = 5000;
const CONNECTION_HELP_DELAY_MS = 120000;

const downloadTextFile = (name, content) => {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
};

function HelpLink({ step }) {
  return (
    <p className="onboarding-help">
      Мы на связи и поможем на любом этапе подключения и настройки.{' '}
      <TelegramContactLink placement={`onboarding_${step}`}>Помощь в Telegram</TelegramContactLink>
    </p>
  );
}

function Progress({ status }) {
  const stages = [
    ['coordinator_count', 'Подключение', status?.coordinator_count > 0],
    ['coordinator_connected', 'Координатор online', status?.coordinator_connected],
    ['first_device_seen', 'Первое устройство', status?.first_device_seen],
    ['zone_created', 'Первая зона', status?.zone_created],
  ];

  return (
    <ol className="onboarding-progress" aria-label="Прогресс настройки">
      {stages.map(([key, label, done], index) => (
        <li key={key} className={done ? 'is-done' : ''}>
          <span>{done ? '✓' : index + 1}</span>{label}
        </li>
      ))}
    </ol>
  );
}

function SecretPanel({ setup, connectionMode, platform, setPlatform, localMqtt, setLocalMqtt }) {
  const bridgeConfig = useMemo(
    () => buildBridgeConfig({ setup, local: localMqtt }),
    [setup, localMqtt],
  );

  if (!setup) return null;

  return (
    <section className="onboarding-card onboarding-secret">
      <div>
        <div className="onboarding-kicker">Показываем один раз</div>
        <h2>Сохраните конфигурацию подключения</h2>
        <p>Пароль не хранится в GrowerHub и не появится снова после перезагрузки страницы. При утрате выпустите новые данные подключения.</p>
      </div>

      <dl className="onboarding-credentials">
        <div><dt>MQTT server</dt><dd>{setup.server}</dd></div>
        <div><dt>Username</dt><dd>{setup.username}</dd></div>
        <div><dt>Password</dt><dd>{setup.password}</dd></div>
        <div><dt>Base topic</dt><dd>{setup.base_topic}</dd></div>
      </dl>

      <div className="onboarding-choice-grid" role="group" aria-label="Платформа установки">
        {[
          [SETUP_PLATFORMS.WINDOWS, 'Windows', 'Мастер с выбором COM-порта и Z-Stack/Ember.'],
          [SETUP_PLATFORMS.LINUX, 'Raspberry Pi / Linux', 'Docker Compose, USB mapping и постоянный volume.'],
          [SETUP_PLATFORMS.MANUAL, 'Вручную', 'Для уже установленного Zigbee2MQTT.'],
        ].map(([value, title, text]) => (
          <button
            type="button"
            className={platform === value ? 'choice-card is-selected' : 'choice-card'}
            key={value}
            onClick={() => setPlatform(value)}
          >
            <strong>{title}</strong><span>{text}</span>
          </button>
        ))}
      </div>

      {connectionMode === CONNECTION_MODES.BRIDGE ? (
        <div className="onboarding-local-mqtt">
          <h3>Локальный MQTT</h3>
          <p>Эти значения используются только для создания файла в браузере и не отправляются GrowerHub.</p>
          <div className="onboarding-fields">
            <label>Адрес<input value={localMqtt.host} onChange={(event) => setLocalMqtt((value) => ({ ...value, host: event.target.value }))} placeholder="192.168.1.10" /></label>
            <label>Порт<input value={localMqtt.port} onChange={(event) => setLocalMqtt((value) => ({ ...value, port: event.target.value }))} inputMode="numeric" /></label>
            <label>Username<input value={localMqtt.username} onChange={(event) => setLocalMqtt((value) => ({ ...value, username: event.target.value }))} autoComplete="off" /></label>
            <label>Password<input type="password" value={localMqtt.password} onChange={(event) => setLocalMqtt((value) => ({ ...value, password: event.target.value }))} autoComplete="new-password" /></label>
          </div>
        </div>
      ) : null}

      <div className="onboarding-actions">
        {connectionMode === CONNECTION_MODES.DIRECT ? (
          <>
            <Button variant="primary" onClick={() => downloadTextFile('configuration.yaml', setup.configuration_yaml)}>Скачать configuration.yaml</Button>
            <Button onClick={() => downloadTextFile('secret.yaml', setup.secret_yaml)}>Скачать secret.yaml</Button>
          </>
        ) : (
          <Button variant="primary" onClick={() => downloadTextFile('bridge.conf', bridgeConfig)}>Скачать личный bridge.conf</Button>
        )}
        {platform !== SETUP_PLATFORMS.MANUAL ? (
          <a className="gh-btn gh-btn--secondary gh-btn--md" href={GITHUB_RELEASES_URL} target="_blank" rel="noreferrer">Открыть пакеты установки</a>
        ) : null}
      </div>
      <p className="onboarding-note">Не публикуйте эти файлы и не отправляйте их в Telegram. GrowerHub никогда не просит прислать пароль MQTT.</p>
    </section>
  );
}

function AppOnboarding() {
  const location = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = useState(null);
  const [coordinators, setCoordinators] = useState([]);
  const [selectedCoordinatorId, setSelectedCoordinatorId] = useState('');
  const [overview, setOverview] = useState(null);
  const [setup, setSetup] = useState(null);
  const [coordinatorName, setCoordinatorName] = useState('Моя ферма');
  const [connectionMode, setConnectionMode] = useState(CONNECTION_MODES.DIRECT);
  const [platform, setPlatform] = useState(SETUP_PLATFORMS.WINDOWS);
  const [localMqtt, setLocalMqtt] = useState({ host: '', port: '1883', username: '', password: '' });
  const [zoneName, setZoneName] = useState('Первая зона');
  const [temperatureChoice, setTemperatureChoice] = useState('');
  const [lightChoice, setLightChoice] = useState('');
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [permitJoinUntil, setPermitJoinUntil] = useState(null);
  const [waitStartedAt, setWaitStartedAt] = useState(Date.now());
  const selectedCoordinator = coordinators.find((item) => item.id === selectedCoordinatorId)
    || coordinators[0]
    || null;

  const refresh = useCallback(async ({ quiet = false } = {}) => {
    if (!quiet) setBusy('loading');
    try {
      const [nextStatus, nextCoordinators] = await Promise.all([
        fetchOnboardingStatus(),
        fetchCoordinators(),
      ]);
      setStatus(nextStatus);
      setCoordinators(nextCoordinators);
      setError('');

      const coordinatorId = selectedCoordinatorId || nextCoordinators[0]?.id;
      if (coordinatorId) {
        setSelectedCoordinatorId(coordinatorId);
        const nextOverview = await fetchCoordinatorOverview(coordinatorId);
        setOverview(nextOverview);
      } else {
        setOverview(null);
      }

      if (nextStatus.coordinator_connected) {
        trackProductGoalOnce(
          'coordinator_connected',
          { step: 'online', connection_mode: connectionMode },
          `coordinator_connected_${coordinatorId || 'unknown'}`,
        );
      }
      if (nextStatus.first_device_seen) {
        trackProductGoalOnce(
          'first_device_seen',
          { step: 'device_detected', connection_mode: connectionMode },
          `first_device_seen_${coordinatorId || 'unknown'}`,
        );
      }
    } catch (requestError) {
      if (!quiet) {
        setError(requestError.status === 503
          ? 'Self-service пока выключен до завершения проверки безопасности. Код интерфейса готов, но подключение ещё не открыто.'
          : requestError.message);
      }
    } finally {
      if (!quiet) setBusy('');
    }
  }, [connectionMode, selectedCoordinatorId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    const timer = window.setInterval(() => refresh({ quiet: true }), POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get('signup') !== 'complete') return;

    trackProductGoalOnce('signup_complete', { step: 'sso_callback' });
    params.delete('signup');
    navigate(`${location.pathname}${params.toString() ? `?${params}` : ''}`, { replace: true });
  }, [location.pathname, location.search, navigate]);

  const temperatureFeatures = useMemo(() => getReadableFeatures(overview, 'temperature'), [overview]);
  const writableSwitches = useMemo(() => getWritableSwitches(overview), [overview]);
  const showConnectionHelp = selectedCoordinator && !status?.coordinator_connected
    && Date.now() - waitStartedAt >= CONNECTION_HELP_DELAY_MS;

  const handleCreateCoordinator = async (event) => {
    event.preventDefault();
    setBusy('create-coordinator');
    setError('');
    try {
      const created = await createCoordinator(coordinatorName.trim());
      setSetup(created.setup);
      setCoordinators([created.coordinator]);
      setSelectedCoordinatorId(created.coordinator.id);
      setWaitStartedAt(Date.now());
      trackProductGoal('coordinator_created', { step: 'credentials_shown', connection_mode: connectionMode });
      await refresh({ quiet: true });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  const handleRotate = async () => {
    if (!selectedCoordinator || !window.confirm('Старый MQTT-пароль перестанет работать. Выпустить новый?')) return;
    setBusy('rotate');
    setError('');
    try {
      setSetup(await rotateCoordinatorCredentials(selectedCoordinator.id));
      setWaitStartedAt(Date.now());
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  const handlePermitJoin = async () => {
    if (!selectedCoordinator) return;
    setBusy('permit-join');
    setError('');
    try {
      await enablePermitJoin(selectedCoordinator.id, 180);
      setPermitJoinUntil(Date.now() + 180000);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  const handleCreateZone = async (event) => {
    event.preventDefault();
    setBusy('create-zone');
    setError('');
    try {
      const afterZone = await createZone(zoneName.trim());
      const zone = [...(afterZone.rooms || [])].reverse().find((item) => item.name === zoneName.trim())
        || afterZone.rooms?.at(-1);
      if (!zone) throw new Error('GrowerHub не вернул созданную зону');

      const afterSection = await createZoneSection(zone.id);
      const updatedZone = afterSection.rooms?.find((item) => item.id === zone.id);
      const section = updatedZone?.boxes?.at(-1);
      const resources = buildSectionResources({
        coordinatorId: selectedCoordinator.id,
        temperatureChoice,
        lightChoice,
        overview,
      });
      if (section && resources.length > 0) {
        await replaceSectionResources(section.id, resources);
      }
      trackProductGoal('zone_created', { step: resources.length ? 'devices_assigned' : 'zone_only' });
      await refresh({ quiet: true });
      navigate('/app/');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  if (busy === 'loading' && !status && !error) {
    return <AppPageState kind="loading" title="Проверяем подключение…" />;
  }

  return (
    <div className="app-onboarding">
      <AppPageHeader title="Первое подключение" />
      <Progress status={status} />
      {error ? <AppPageState kind="error" title={error}><HelpLink step="error" /></AppPageState> : null}

      {!selectedCoordinator ? (
        <section className="onboarding-card">
          <div className="onboarding-kicker">Шаг 1</div>
          <h2>Создайте подключение</h2>
          <p>Название нужно только вам. Культуры, число растений и подробную схему фермы указывать не требуется.</p>
          <form className="onboarding-form" onSubmit={handleCreateCoordinator}>
            <label htmlFor="coordinator-name">Название координатора</label>
            <input id="coordinator-name" value={coordinatorName} onChange={(event) => setCoordinatorName(event.target.value)} maxLength="120" required />
            <Button type="submit" variant="primary" isLoading={busy === 'create-coordinator'}>Создать координатор</Button>
          </form>
          <HelpLink step="create_coordinator" />
        </section>
      ) : (
        <>
          <section className="onboarding-card">
            <div className="onboarding-kicker">Шаг 2</div>
            <h2>Как вы подключаетесь?</h2>
            <div className="onboarding-choice-grid">
              <button type="button" className={connectionMode === CONNECTION_MODES.DIRECT ? 'choice-card is-selected' : 'choice-card'} onClick={() => setConnectionMode(CONNECTION_MODES.DIRECT)}>
                <strong>Новая установка</strong><span>Zigbee2MQTT подключится к GrowerHub напрямую.</span>
              </button>
              <button type="button" className={connectionMode === CONNECTION_MODES.BRIDGE ? 'choice-card is-selected' : 'choice-card'} onClick={() => setConnectionMode(CONNECTION_MODES.BRIDGE)}>
                <strong>Уже есть Zigbee2MQTT / Home Assistant</strong><span>Connector сохранит локальный MQTT и передаст только нужные топики.</span>
              </button>
            </div>
          </section>

          {setup ? (
            <SecretPanel
              setup={setup}
              connectionMode={connectionMode}
              platform={platform}
              setPlatform={setPlatform}
              localMqtt={localMqtt}
              setLocalMqtt={setLocalMqtt}
            />
          ) : (
            <section className="onboarding-card">
              <h2>Нужна новая копия конфигурации?</h2>
              <p>Секрет уже был показан и не хранится на сервере. Ротация сразу отзовёт прежний MQTT-пароль.</p>
              <Button onClick={handleRotate} isLoading={busy === 'rotate'}>Выпустить новые данные</Button>
            </section>
          )}

          {!status?.coordinator_connected ? (
            <section className="onboarding-card onboarding-wait">
              <span className="status-pulse" aria-hidden="true" />
              <div><h2>Ждём координатор</h2><p>Запустите пакет или Zigbee2MQTT. Статус обновится автоматически.</p></div>
              {showConnectionHelp ? (
                <div className="onboarding-diagnostics">
                  <strong>Что проверить</strong>
                  <ul><li>порт USB и тип адаптера;</li><li>доступ к `growerhub.ru:8883`;</li><li>файлы `configuration.yaml` и `secret.yaml` рядом с данными Zigbee2MQTT.</li></ul>
                </div>
              ) : null}
              <HelpLink step="wait_online" />
            </section>
          ) : null}

          {status?.coordinator_connected && !status?.first_device_seen ? (
            <section className="onboarding-card">
              <div className="onboarding-kicker">Шаг 3</div>
              <h2>{connectionMode === CONNECTION_MODES.BRIDGE ? 'Импортируем существующие устройства' : 'Добавьте первое устройство'}</h2>
              {connectionMode === CONNECTION_MODES.BRIDGE ? (
                <p>GrowerHub ждёт список устройств от вашего Zigbee2MQTT. Обычно они появляются автоматически после подключения connector.</p>
              ) : (
                <><p>Разрешите подключение на три минуты, затем переведите датчик или розетку в режим сопряжения.</p><Button variant="primary" onClick={handlePermitJoin} isLoading={busy === 'permit-join'}>Разрешить подключение на 3 минуты</Button></>
              )}
              {permitJoinUntil && permitJoinUntil > Date.now() ? <p className="status-ok">Подключение разрешено</p> : null}
              <HelpLink step="first_device" />
            </section>
          ) : null}

          {status?.first_device_seen && !status?.zone_created ? (
            <section className="onboarding-card">
              <div className="onboarding-kicker">Шаг 4</div>
              <h2>Создайте первую зону</h2>
              <p>Достаточно названия. Найденные устройства можно сразу назначить зоне или сделать это позже.</p>
              <form className="onboarding-form" onSubmit={handleCreateZone}>
                <label htmlFor="zone-name">Название зоны</label>
                <input id="zone-name" value={zoneName} onChange={(event) => setZoneName(event.target.value)} maxLength="120" required />

                {temperatureFeatures.length > 0 ? (
                  <label>Датчик температуры
                    <select value={temperatureChoice} onChange={(event) => setTemperatureChoice(event.target.value)}>
                      <option value="">Назначить позже</option>
                      {temperatureFeatures.map(({ device, feature }) => <option key={`${device.ieee_address}-${feature.property}`} value={encodeFeatureChoice(device, feature)}>{device.friendly_name} · {feature.label || feature.property}</option>)}
                    </select>
                  </label>
                ) : null}

                {writableSwitches.length > 0 ? (
                  <label>Розетка или реле для света
                    <select value={lightChoice} onChange={(event) => setLightChoice(event.target.value)}>
                      <option value="">Назначить позже</option>
                      {writableSwitches.map(({ device, feature }) => <option key={`${device.ieee_address}-${feature.property}`} value={encodeFeatureChoice(device, feature)}>{device.friendly_name} · {feature.label || feature.property}</option>)}
                    </select>
                  </label>
                ) : null}

                <Button type="submit" variant="primary" isLoading={busy === 'create-zone'}>Создать зону и открыть обзор</Button>
              </form>
              <HelpLink step="create_zone" />
            </section>
          ) : null}

          {status?.zone_created ? (
            <section className="onboarding-card onboarding-complete">
              <div className="onboarding-kicker">Готово</div>
              <h2>Базовая настройка завершена</h2>
              <p>Данные устройств уже доступны в кабинете. Автоматизации можно включить позже.</p>
              <div className="onboarding-actions"><Link className="hero-cta" to="/app/">Открыть обзор</Link><Link className="secondary-link" to="/app/automations/">Настроить автоматизацию</Link></div>
              <HelpLink step="complete" />
            </section>
          ) : null}
        </>
      )}
    </div>
  );
}

export default AppOnboarding;
