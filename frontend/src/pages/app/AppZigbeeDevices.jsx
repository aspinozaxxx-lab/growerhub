import { useEffect, useState } from 'react';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import TelegramContactLink from '../../components/TelegramContactLink';
import { fetchAutomationOverview, setZigbeeProperty } from '../../api/selfService';
import './SelfServicePages.css';

const displayValue = (value, unit) => {
  if (value === null || value === undefined || value === '') return '—';
  return `${String(value)}${unit ? ` ${unit}` : ''}`;
};

function AppZigbeeDevices() {
  const [overview, setOverview] = useState(null);
  const [busy, setBusy] = useState('loading');
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setOverview(await fetchAutomationOverview());
      setError('');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  useEffect(() => { load(); }, []);

  const setProperty = async (device, feature, value) => {
    const key = `${device.coordinator_id}:${device.ieee_address}:${feature.property}`;
    setBusy(key);
    try {
      await setZigbeeProperty(device.coordinator_id, device.ieee_address, feature.property, value);
      window.setTimeout(load, 800);
    } catch (requestError) {
      setError(requestError.message);
      setBusy('');
    }
  };

  if (busy === 'loading') return <AppPageState kind="loading" title="Загружаем устройства…" />;
  const devices = overview?.resource_catalog?.zigbee_devices || [];

  return (
    <div className="self-service-page">
      <AppPageHeader title="Устройства" />
      {error ? <AppPageState kind="error" title={error} /> : null}
      {devices.length === 0 ? <AppPageState kind="empty" title="Zigbee-устройства пока не найдены" hint="Откройте «Подключения» или продолжите первое подключение." /> : null}
      <div className="device-grid">
        {devices.map((device) => (
          <article className="device-card-v2" key={`${device.coordinator_id}:${device.ieee_address}`}>
            <div className="device-card-v2__header"><div><h2>{device.friendly_name}</h2><p>{device.coordinator_name}</p></div><span className={device.availability === 'online' ? 'status-chip is-online' : 'status-chip'}>{device.availability || 'неизвестно'}</span></div>
            <div className="metric-grid">
              {(device.metrics || []).map((feature) => <div key={feature.property}><span>{feature.label || feature.property}</span><strong>{displayValue(feature.value, feature.unit)}</strong></div>)}
            </div>
            {(device.controls || []).length > 0 ? (
              <div className="device-controls">
                {(device.controls || []).map((feature) => {
                  const key = `${device.coordinator_id}:${device.ieee_address}:${feature.property}`;
                  if (feature.property !== 'state') return <span key={feature.property} className="readonly-control">{feature.label || feature.property}: управление пока недоступно в простом интерфейсе</span>;
                  return <div key={feature.property}><span>{feature.label || 'Питание'}</span><Button onClick={() => setProperty(device, feature, feature.value_on || 'ON')} isLoading={busy === key}>Включить</Button><Button onClick={() => setProperty(device, feature, feature.value_off || 'OFF')} disabled={busy === key}>Выключить</Button></div>;
                })}
              </div>
            ) : (
              <p className="device-readonly">Устройство обнаружено и доступно для мониторинга. Неизвестные функции остаются read-only. <TelegramContactLink placement="unsupported_device">Запросить поддержку модели</TelegramContactLink></p>
            )}
          </article>
        ))}
      </div>
    </div>
  );
}

export default AppZigbeeDevices;
