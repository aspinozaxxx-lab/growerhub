import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import TelegramContactLink from '../../components/TelegramContactLink';
import DemoStartLink from '../../components/DemoStartLink';
import Button from '../../components/ui/Button';
import PushokConnect from '../../components/PushokConnect';
import { pushokError } from '../../utils/pushokConnection';
import NetworkCoordinatorPilot from '../../components/NetworkCoordinatorPilot';
import CoordinatorSetup, { CoordinatorConnectionMode } from '../../components/CoordinatorSetup';
import { CONNECTION_MODES } from '../../domain/coordinatorSetup';
import { getPublicPath } from '../../domain/localizedRoutes';
import {
  createCoordinator,
  completeOnboarding,
  createGreenhouse,
  createUserFarm,
  enablePermitJoin,
  fetchCoordinatorOverview,
  fetchCoordinators,
  fetchFarmsOverview,
  fetchOnboardingStatus,
  replaceGreenhouseSlots,
  rotateCoordinatorCredentials,
} from '../../api/selfService';
import { useAuth } from '../../features/auth/AuthContext';
import { trackProductGoal, trackProductGoalOnce } from '../../utils/analytics';
import {
  buildSectionResources,
  encodeFeatureChoice,
  getReadableFeatures,
  getWritableSwitches,
} from './onboardingModel';
import './AppOnboarding.css';
import { getCurrentLocale, translateApp } from '../../locales/i18n';

const POLL_INTERVAL_MS = 5000;
const CONNECTION_HELP_DELAY_MS = 120000;

function HelpLink({ step }) {
  return (
    <p className="onboarding-help">{translateApp("Поможем подключить первый датчик. Напишите модель координатора, Windows или Linux и есть ли у вас Home Assistant.")}{' '}
      <TelegramContactLink placement={`onboarding_${step}`}>{translateApp("Помощь в Telegram")}</TelegramContactLink>
    </p>
  );
}

function Progress({ status }) {
  const stages = [
    ['coordinator_count', translateApp("Подключение"), status?.coordinator_count > 0],
    ['coordinator_connected', translateApp("Координатор в сети"), status?.coordinator_connected],
    ['first_device_seen', translateApp("Первое устройство"), status?.first_device_seen],
    ['zone_created', translateApp("Первая теплица"), status?.zone_created],
  ];

  return (
    <ol className="onboarding-progress" aria-label={translateApp("Прогресс настройки")}>
      {stages.map(([key, label, done], index) => (
        <li key={key} className={done ? 'is-done' : ''}>
          <span>{done ? '✓' : index + 1}</span>{label}
        </li>
      ))}
    </ol>
  );
}

function AppOnboarding() {
  const { loadCurrentUser } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState(null);
  const [coordinators, setCoordinators] = useState([]);
  const [selectedCoordinatorId, setSelectedCoordinatorId] = useState('');
  const [overview, setOverview] = useState(null);
  const [setup, setSetup] = useState(null);
  const [coordinatorName, setCoordinatorName] = useState(translateApp("Моя ферма"));
  const [connectionMode, setConnectionMode] = useState(null);
  const [zoneName, setZoneName] = useState(translateApp("Первая теплица"));
  const [temperatureChoice, setTemperatureChoice] = useState('');
  const [humidityChoice, setHumidityChoice] = useState('');
  const [soilMoistureChoice, setSoilMoistureChoice] = useState('');
  const [lightChoice, setLightChoice] = useState('');
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [permitJoinUntil, setPermitJoinUntil] = useState(null);
  const [waitStartedAt, setWaitStartedAt] = useState(Date.now());
  const selectedCoordinator = coordinators.find((item) => item.id === selectedCoordinatorId)
    || coordinators[0]
    || null;
  const setupComplete = status?.step === 'COMPLETE';

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
          ? translateApp("Подключение временно недоступно. Попробуйте ещё раз чуть позже или напишите нам в Telegram — поможем разобраться.")
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

  const temperatureFeatures = useMemo(() => getReadableFeatures(overview, 'temperature'), [overview]);
  const humidityFeatures = useMemo(() => getReadableFeatures(overview, 'humidity'), [overview]);
  const soilMoistureFeatures = useMemo(() => getReadableFeatures(overview, 'soil_moisture'), [overview]);
  const writableSwitches = useMemo(() => getWritableSwitches(overview), [overview]);
  const showConnectionHelp = selectedCoordinator && !status?.coordinator_connected
    && Date.now() - waitStartedAt >= CONNECTION_HELP_DELAY_MS;
  const isPushok = selectedCoordinator?.transport === 'PUSHOK_CLOUD';
  const handlePushokCreated = (coordinator) => {
    setSelectedCoordinatorId(coordinator.id);
    setSetup(null);
    setWaitStartedAt(Date.now());
    refresh({ quiet: true });
  };

  const handleCreateCoordinator = async (event) => {
    event.preventDefault();
    if (!connectionMode) return;
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
    if (!selectedCoordinator || !window.confirm(translateApp("Старый MQTT-пароль перестанет работать. Выпустить новый?"))) return;
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
      let currentFarms = await fetchFarmsOverview();
      if (!currentFarms?.farms?.length) {
        currentFarms = await createUserFarm({
          name: translateApp("Моя ферма"),
          enabled: true,
        });
      }
      const farm = currentFarms.farms[0];
      const afterGreenhouse = await createGreenhouse(
        farm.id,
        { name: zoneName.trim(), enabled: true },
      );
      const greenhouses = afterGreenhouse.farms
        .find((item) => item.id === farm.id)?.greenhouses || [];
      const greenhouse = [...greenhouses].reverse()
        .find((item) => item.name === zoneName.trim()) || greenhouses.at(-1);
      if (!greenhouse) throw new Error(translateApp("GrowerHub не вернул созданную теплицу"));

      const resources = buildSectionResources({
        coordinatorId: selectedCoordinator.id,
        temperatureChoice,
        humidityChoice,
        soilMoistureChoice,
        lightChoice,
        overview,
      });
      if (resources.length > 0) {
        await replaceGreenhouseSlots(greenhouse.id, resources);
      }
      await completeOnboarding();
      await loadCurrentUser();
      trackProductGoal('zone_created', { step: resources.length ? 'devices_assigned' : 'zone_only' });
      await refresh({ quiet: true });
      navigate('/app/');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  useEffect(() => {
    if (status?.step !== 'COMPLETE' || status?.completed) return undefined;
    let cancelled = false;
    completeOnboarding()
      .then(() => {
        if (!cancelled) return loadCurrentUser();
        return null;
      })
      .then(() => {
        if (!cancelled) setStatus((current) => (current ? { ...current, completed: true } : current));
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [loadCurrentUser, status?.completed, status?.step]);

  if (busy === 'loading' && !status && !error) {
    return <AppPageState kind="loading" title={translateApp("Проверяем подключение…")} />;
  }

  return (
    <div className="app-onboarding">
      <AppPageHeader title={translateApp("Первое подключение")} />
      <Progress status={status} />
      {error ? <AppPageState kind="error" title={error}><HelpLink step="error" /></AppPageState> : null}

      {setupComplete ? (
        <section className="onboarding-card onboarding-complete">
          <div className="onboarding-kicker">{translateApp("Готово")}</div>
          <h2>{translateApp("Базовая настройка завершена")}</h2>
          <p>{translateApp("Данные устройств уже доступны в кабинете. Автоматизации можно включить позже.")}</p>
          <div className="onboarding-actions">
            <Link className="hero-cta" to="/app/">{translateApp("Открыть обзор")}</Link>
            <Link className="secondary-link" to="/app/automations/">{translateApp("Настроить автоматизацию")}</Link>
            <Link className="secondary-link" to="/app/settings/connections/">{translateApp("Управлять подключениями")}</Link>
          </div>
          <p className="onboarding-help"><TelegramContactLink placement="onboarding_complete">{translateApp("Помощь в Telegram")}</TelegramContactLink></p>
        </section>
      ) : (
        <section className="onboarding-card onboarding-choices">
          <h2>{translateApp("Что у вас уже есть?")}</h2>
          <p>{translateApp("Выберите свой вариант — подскажем следующий шаг.")}</p>
          <CoordinatorConnectionMode value={connectionMode} onChange={setConnectionMode}>
            <PushokConnect card onCreated={handlePushokCreated} />
            <DemoStartLink placement="onboarding_no_equipment" className="choice-card">
              <strong>{translateApp("Хочу попробовать без оборудования")}</strong><span>{translateApp("Откройте демоферму: датчики, растения и сценарии уже настроены.")}</span>
            </DemoStartLink>
          </CoordinatorConnectionMode>
          <p>{translateApp("Оборудование ещё не выбрано?")}{' '}<Link to={getPublicPath('equipment', getCurrentLocale())}>{translateApp("Посмотреть совместимые устройства")}</Link></p>
          <div className="onboarding-pilots">
            <h3>{translateApp("Пилотные подключения")}</h3>
            <div className="onboarding-actions"><NetworkCoordinatorPilot placement="onboarding_zs_eht_pilot" /></div>
          </div>
        </section>
      )}

      {!setupComplete && (connectionMode || isPushok) && (!selectedCoordinator ? (
        <section className="onboarding-card">
          <div className="onboarding-kicker">{translateApp("Шаг 1")}</div>
          <h2>{translateApp("Создайте подключение")}</h2>
          <p>{translateApp("Название нужно только вам. Культуры, число растений и подробную схему фермы указывать не требуется.")}</p>
          <form className="onboarding-form" onSubmit={handleCreateCoordinator}>
            <label htmlFor="coordinator-name">{translateApp("Название подключения")}</label>
            <input id="coordinator-name" value={coordinatorName} onChange={(event) => setCoordinatorName(event.target.value)} maxLength="120" required />
            <Button type="submit" variant="primary" isLoading={busy === 'create-coordinator'}>{translateApp("Создать подключение")}</Button>
          </form>
          <HelpLink step="create_coordinator" />
        </section>
      ) : (
        <>
          {setup ? (
            <CoordinatorSetup
              setup={setup}
              connectionMode={connectionMode}
            />
          ) : !status?.coordinator_connected && !isPushok ? (
            <section className="onboarding-card">
              <h2>{translateApp("Нужна новая копия конфигурации?")}</h2>
              <p>{translateApp("Секрет уже был показан и не хранится на сервере. Ротация сразу отзовёт прежний MQTT-пароль.")}</p>
              <Button onClick={handleRotate} isLoading={busy === 'rotate'}>{translateApp("Выпустить новые данные")}</Button>
            </section>
          ) : null}

          {isPushok && !status?.coordinator_connected ? (
            <section className="onboarding-card" role="status"><h2>{translateApp('Подключение ПушОк')}</h2>
              <p>{selectedCoordinator.connection_error ? pushokError(selectedCoordinator.connection_error)
                : translateApp('Ждём связь со шлюзом через интернет. Проверьте, что он доступен в Управляторе.')}</p>
              {selectedCoordinator.connection_status === 'ERROR' ? <PushokConnect coordinator={selectedCoordinator} onCreated={handlePushokCreated} /> : null}
            </section>
          ) : null}
          {!status?.coordinator_connected && !isPushok ? (
            <section className="onboarding-card onboarding-wait">
              <span className="status-pulse" aria-hidden="true" />
              <div><h2>{translateApp("Ждём координатор")}</h2><p>{connectionMode === CONNECTION_MODES.BRIDGE
                ? translateApp("Запустите модуль связи. Статус обновится автоматически.")
                : translateApp("Запустите пакет или Zigbee2MQTT. Статус обновится автоматически.")}</p></div>
              {showConnectionHelp ? (
                <div className="onboarding-diagnostics">
                  <strong>{translateApp("Что проверить")}</strong>
                  {connectionMode === CONNECTION_MODES.BRIDGE ? (
                    <ul>
                      <li>{translateApp("Проверьте, что Docker работает, а bridge.conf находится рядом с docker-compose.yml.")}</li>
                      <li>{translateApp("Проверьте адрес, порт, базовую тему и учётные данные локального MQTT-брокера.")}</li>
                      <li>{translateApp("Разрешите исходящие подключения к growerhub.ru:8883.")}</li>
                      <li>{translateApp("Посмотрите причину ошибки:")} <code>docker compose logs --tail=50 connector</code>.</li>
                    </ul>
                  ) : (
                    <ul><li>{translateApp("порт USB и тип адаптера;")}</li><li>{translateApp("доступ к `growerhub.ru:8883`;")}</li><li>{translateApp("файлы `configuration.yaml` и `secret.yaml` рядом с данными Zigbee2MQTT.")}</li></ul>
                  )}
                </div>
              ) : null}
              <HelpLink step="wait_online" />
            </section>
          ) : null}

          {status?.coordinator_connected && !status?.first_device_seen ? (
            <section className="onboarding-card">
              <div className="onboarding-kicker">{translateApp("Шаг 3")}</div>
              <h2>{connectionMode === CONNECTION_MODES.BRIDGE ? translateApp("Импортируем существующие устройства") : translateApp("Добавьте первое устройство")}</h2>
              {isPushok ? (
                <p>{translateApp('Добавьте датчик или розетку в Управляторе. В течение минуты устройство появится здесь автоматически.')}</p>
              ) : connectionMode === CONNECTION_MODES.BRIDGE ? (
                <p>{translateApp("GrowerHub ждёт список устройств от вашего Zigbee2MQTT. Обычно они появляются автоматически после подключения модуля связи.")}</p>
              ) : (
                <><p>{translateApp("Разрешите подключение на три минуты, затем переведите датчик или розетку в режим сопряжения.")}</p><Button variant="primary" onClick={handlePermitJoin} isLoading={busy === 'permit-join'}>{translateApp("Разрешить подключение на 3 минуты")}</Button></>
              )}
              {permitJoinUntil && permitJoinUntil > Date.now() ? <p className="status-ok">{translateApp("Подключение разрешено")}</p> : null}
              <HelpLink step="first_device" />
            </section>
          ) : null}

          {status?.first_device_seen && !status?.zone_created ? (
            <section className="onboarding-card">
              <div className="onboarding-kicker">{translateApp("Шаг 4")}</div>
              <h2>{translateApp("Создайте первую теплицу")}</h2>
              <p>{translateApp("Назначьте найденные устройства, чтобы увидеть их состояние в Обзоре. Это можно сделать и позже в Конструкторе фермы.")}</p>
              <form className="onboarding-form" onSubmit={handleCreateZone}>
                <label htmlFor="zone-name">{translateApp("Название теплицы")}</label>
                <input id="zone-name" value={zoneName} onChange={(event) => setZoneName(event.target.value)} maxLength="120" required />

                {[
                  { label: 'Датчик температуры', features: temperatureFeatures, value: temperatureChoice, setValue: setTemperatureChoice },
                  { label: 'Влажность воздуха', features: humidityFeatures, value: humidityChoice, setValue: setHumidityChoice },
                  { label: 'Влажность почвы', features: soilMoistureFeatures, value: soilMoistureChoice, setValue: setSoilMoistureChoice },
                ].filter(({ features }) => features.length > 0).map(({ label, features, value, setValue }) => (
                  <label key={label}>{translateApp(label)}<select value={value} onChange={(event) => setValue(event.target.value)}>
                      <option value="">{translateApp("Назначить позже")}</option>
                      {features.map(({ device, feature }) => <option key={`${device.ieee_address}-${feature.property}`} value={encodeFeatureChoice(device, feature)}>{device.friendly_name} · {feature.label || feature.property}</option>)}
                    </select>
                  </label>
                ))}

                {writableSwitches.length > 0 ? (
                  <label>{translateApp("Розетка или реле для света")}<select value={lightChoice} onChange={(event) => setLightChoice(event.target.value)}>
                      <option value="">{translateApp("Назначить позже")}</option>
                      {writableSwitches.map(({ device, feature }) => <option key={`${device.ieee_address}-${feature.property}`} value={encodeFeatureChoice(device, feature)}>{device.friendly_name} · {feature.label || feature.property}</option>)}
                    </select>
                  </label>
                ) : null}

                <Button type="submit" variant="primary" isLoading={busy === 'create-zone'}>{translateApp("Создать теплицу и открыть обзор")}</Button>
              </form>
              <HelpLink step="create_zone" />
            </section>
          ) : null}

        </>
      ))}
    </div>
  );
}

export default AppOnboarding;
