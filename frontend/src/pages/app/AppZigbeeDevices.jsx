import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import TelegramContactLink from '../../components/TelegramContactLink';
import { fetchFarmOverview, setZigbeeProperty } from '../../api/selfService';
import {
  SLOT_ROLE_LABELS,
  assignmentsForZigbeeDevice,
  filterZigbeeDevices,
  priorityDeviceMetrics,
} from '../../features/farm/farmModel';
import { formatDateTime } from './admin/adminFarmDashboardModel';
import { translateApp } from '../../locales/i18n';
import './SelfServicePages.css';

const displayValue = (value, unit) => {
  if (value === null || value === undefined || value === '') return '—';
  return `${String(value)}${unit ? ` ${unit}` : ''}`;
};

const availabilityLabel = (availability) => {
  if (availability === 'online') return translateApp("В сети");
  if (availability === 'offline') return translateApp("Не в сети");
  return translateApp("Статус неизвестен");
};

function deviceModel(device) {
  const definition = device?.definition && typeof device.definition === 'object'
    ? device.definition
    : {};
  return [definition.vendor, definition.model].filter(Boolean).join(' · ');
}

function AppZigbeeDevices() {
  const [overview, setOverview] = useState(null);
  const [busy, setBusy] = useState('loading');
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [availability, setAvailability] = useState('all');

  const load = useCallback(async () => {
    try {
      setOverview(await fetchFarmOverview());
      setError('');
    } catch (requestError) {
      setError(requestError?.message || translateApp("Не удалось загрузить устройства"));
    } finally {
      setBusy('');
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const setProperty = async (device, feature, value) => {
    const key = `${device.coordinator_id}:${device.ieee_address}:${feature.property}`;
    setBusy(key);
    setError('');
    try {
      await setZigbeeProperty(
        device.coordinator_id,
        device.ieee_address,
        feature.property,
        value,
      );
      window.setTimeout(load, 800);
    } catch (requestError) {
      setError(requestError?.message || translateApp("Не удалось изменить состояние устройства"));
      setBusy('');
    }
  };

  const devices = useMemo(
    () => overview?.resource_catalog?.zigbee_devices || [],
    [overview],
  );
  const visibleDevices = useMemo(
    () => filterZigbeeDevices(devices, query, availability),
    [availability, devices, query],
  );

  if (busy === 'loading') {
    return <AppPageState kind="loading" title={translateApp("Загружаем устройства…")} />;
  }

  return (
    <div className="self-service-page farm-devices-page">
      <AppPageHeader
        title={translateApp("Устройства")}
        subtitle={translateApp("Состояние, ключевые показатели и роли устройств в ферме")}
        right={(
          <Link className="gh-btn gh-btn--secondary gh-btn--md" to="/app/farm/">
            {translateApp("Открыть конструктор")}
          </Link>
        )}
      />
      {error ? <AppPageState kind="error" title={error} /> : null}

      <div className="farm-device-filters">
        <label>
          <span>{translateApp("Поиск")}</span>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={translateApp("Название, модель или IEEE-адрес")}
          />
        </label>
        <label>
          <span>{translateApp("Состояние")}</span>
          <select value={availability} onChange={(event) => setAvailability(event.target.value)}>
            <option value="all">{translateApp("Все устройства")}</option>
            <option value="online">{translateApp("В сети")}</option>
            <option value="offline">{translateApp("Не в сети")}</option>
          </select>
        </label>
      </div>

      {devices.length === 0 ? (
        <AppPageState
          kind="empty"
          title={translateApp("Zigbee-устройства пока не найдены")}
          hint={translateApp("Откройте «Подключения» или продолжите первое подключение.")}
        />
      ) : null}
      {devices.length > 0 && visibleDevices.length === 0 ? (
        <AppPageState kind="empty" title={translateApp("По заданным условиям устройств нет")} />
      ) : null}

      <div className="farm-device-grid">
        {visibleDevices.map((device) => {
          const keyPrefix = `${device.coordinator_id}:${device.ieee_address}`;
          const stateControl = (device.controls || [])
            .find((feature) => feature.property === 'state');
          const assignments = assignmentsForZigbeeDevice(overview, device);
          const priorityMetrics = priorityDeviceMetrics(device);
          const technicalFeatures = [
            ...(device.metrics || []).map((feature) => ({ ...feature, section: 'metric' })),
            ...(device.controls || []).map((feature) => ({ ...feature, section: 'control' })),
          ];
          return (
            <article className="farm-device-card" key={keyPrefix}>
              <header className="farm-device-card__header">
                <div>
                  <h2>{device.friendly_name || device.ieee_address}</h2>
                  <p>{deviceModel(device) || device.type || translateApp("Zigbee-устройство")}</p>
                </div>
                <span className={device.availability === 'online' ? 'status-chip is-online' : 'status-chip'}>
                  {availabilityLabel(device.availability)}
                </span>
              </header>

              <div className="farm-device-card__metrics">
                {priorityMetrics.length > 0 ? priorityMetrics.map((feature) => (
                  <div key={feature.property}>
                    <span>{feature.label || feature.property}</span>
                    <strong>{displayValue(feature.value, feature.unit)}</strong>
                  </div>
                )) : <p>{translateApp("Нет текущих показателей")}</p>}
              </div>

              <div className="farm-device-card__roles">
                <span>{translateApp("Роли в ферме")}</span>
                {assignments.length > 0 ? (
                  <div>
                    {assignments.map((assignment) => (
                      <Link
                        key={`${assignment.zoneId}:${assignment.role}`}
                        to="/app/farm/"
                        title={translateApp("Изменить назначение в Конструкторе фермы")}
                      >
                        {assignment.zoneName} · {translateApp(SLOT_ROLE_LABELS[assignment.role] || assignment.role)}
                      </Link>
                    ))}
                  </div>
                ) : (
                  <Link to="/app/farm/">{translateApp("Не назначено — открыть конструктор")}</Link>
                )}
              </div>

              {stateControl ? (
                <div className="farm-device-card__state">
                  <span>{stateControl.label || translateApp("Питание")}</span>
                  <strong>{displayValue(stateControl.value, stateControl.unit)}</strong>
                  <div>
                    <Button
                      size="sm"
                      onClick={() => setProperty(device, stateControl, stateControl.value_on || 'ON')}
                      isLoading={busy === `${keyPrefix}:${stateControl.property}`}
                    >
                      {translateApp("Включить")}
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => setProperty(device, stateControl, stateControl.value_off || 'OFF')}
                      disabled={busy === `${keyPrefix}:${stateControl.property}`}
                    >
                      {translateApp("Выключить")}
                    </Button>
                  </div>
                </div>
              ) : null}

              <details className="farm-device-card__details">
                <summary>{translateApp("Подробнее")}</summary>
                <dl>
                  <div>
                    <dt>{translateApp("IEEE-адрес")}</dt>
                    <dd>{device.ieee_address || '—'}</dd>
                  </div>
                  <div>
                    <dt>{translateApp("Координатор")}</dt>
                    <dd>{device.coordinator_name || '—'}</dd>
                  </div>
                  <div>
                    <dt>{translateApp("Последние данные")}</dt>
                    <dd>{formatDateTime(device.last_state_at, '—')}</dd>
                  </div>
                </dl>
                <div className="farm-device-card__all-metrics">
                  {technicalFeatures.map((feature) => (
                    <div key={`${feature.section}:${feature.type}:${feature.property}`}>
                      <span>{feature.label || feature.property}</span>
                      <strong>{displayValue(feature.value, feature.unit)}</strong>
                    </div>
                  ))}
                </div>
                {!stateControl && (device.controls || []).length > 0 ? (
                  <p className="device-readonly">
                    {translateApp("Неизвестные функции доступны только для чтения.")}
                    {' '}
                    <TelegramContactLink placement="unsupported_device">
                      {translateApp("Запросить поддержку модели")}
                    </TelegramContactLink>
                  </p>
                ) : null}
              </details>
            </article>
          );
        })}
      </div>
    </div>
  );
}

export default AppZigbeeDevices;
