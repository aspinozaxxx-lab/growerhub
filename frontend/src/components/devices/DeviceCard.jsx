import React from 'react';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import { resolveDeviceAsset } from './assets';
import { formatSensorValue } from '../../utils/formatters';
import './DeviceCard.css';

// Translitem: DeviceCard - komponent otobrazheniya ustrojstva s dvumya variantami (default dlya stranicy ustrojstv, plant dlya vlozheniya v kartochku rastenija).
// Translitem: Ispolzuetsya v AppDevices.jsx; pokazyvaet avatar ustrojstva, status online/offline, nazvanie, firmware i osnovnye sensornye znacheniya.
function StatusBadge({ isOnline }) {
  return (
    <div className="device-card__status">
      <span className={`status-dot ${isOnline ? 'is-online' : 'is-offline'}`} aria-hidden="true" />
      {isOnline ? 'РћРЅР»Р°Р№РЅ' : 'РћС„С„Р»Р°Р№РЅ'}
    </div>
  );
}

function MetricPill({ label, value, metric, deviceId, onOpenStats }) {
  const display = formatSensorValue(value);
  const handleClick = () => {
    if (deviceId && metric) {
      onOpenStats({ deviceId, metric });
    }
  };
  return (
    <button
      type="button"
      className="metric-pill"
      onClick={handleClick}
      disabled={!deviceId || !metric}
    >
      <span className="metric-pill__label">{label}</span>
      <span className="metric-pill__value">{display}</span>
    </button>
  );
}

function DeviceCard({ device, onEdit, variant = 'default' }) {
  // Translitem: device - dannye ustrojstva; onEdit - handler redaktirovaniya; variant - tip otobrazheniya (default|plant).
  const { openSensorStats } = useSensorStatsContext();
  const firmware = device.firmware_version || device.current_version || 'n/a';
  const wateringSpeed = device.watering_speed_lph;
  // avatarKey - segodnya odna ikonka dlya vseh ustrojstv; kogda poyavyatsya tipy, klyuch mozhno brat' iz dannyh
  const avatarKey = 'grovika_mini';
  const avatarSrc = resolveDeviceAsset(avatarKey);
  const displayName = device.name || 'РЈСЃС‚СЂРѕР№СЃС‚РІРѕ';

  const handleEdit = () => {
    if (onEdit) {
      onEdit(device);
    }
  };

  // Translitem: vetka default - tekushchij vid dlya stranicy ustrojstv.
  const renderDefault = () => (
    <div className="device-card device-card--default">
      <div className="device-card__header">
        <div>
          <div className="device-card__title">{displayName}</div>
          <div className="device-card__subtitle">{device.device_id}</div>
          <StatusBadge isOnline={device.is_online} />
        </div>
        <button type="button" className="device-card__edit" onClick={handleEdit} aria-label="Р РµРґР°РєС‚РёСЂРѕРІР°С‚СЊ">
          ??
        </button>
      </div>

      <div className="device-card__body">
        <div className="device-card__avatar" aria-hidden="true">
          <img src={avatarSrc} alt="device avatar" />
        </div>
        <div className="device-card__info">
          <div className="device-card__fw">РџСЂРѕС€РёРІРєР°: {firmware}</div>
          <div className="device-card__metrics">
            <MetricPill
              label="Р’Р»Р°Р¶РЅ. РїРѕС‡РІС‹, %"
              value={device.soil_moisture}
              metric="soil_moisture"
              deviceId={device.device_id}
              onOpenStats={openSensorStats}
            />
            <MetricPill
              label="Р’Р»Р°Р¶РЅ. РІРѕР·РґСѓС…Р°, %"
              value={device.air_humidity}
              metric="air_humidity"
              deviceId={device.device_id}
              onOpenStats={openSensorStats}
            />
            <MetricPill
              label="T, В°C"
              value={device.air_temperature}
              metric="air_temperature"
              deviceId={device.device_id}
              onOpenStats={openSensorStats}
            />
            <button type="button" className="metric-pill pump-pill" onClick={handleEdit}>
              <span className="metric-pill__label">РќР°СЃРѕСЃ</span>
              <span className="metric-pill__value">
                {wateringSpeed ? `РЎРєРѕСЂРѕСЃС‚СЊ: ${wateringSpeed} Р»/С‡` : 'РЅРµ Р·Р°РґР°РЅРѕ'}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );

  // Translitem: vetka plant - kompaktnyj vid dlya vlozheniya v kartochku rastenija.
  const renderPlant = () => (
    <div className="device-card device-card--plant">
      <div className="device-card__plant-header">
        <div className="device-card__avatar device-card__avatar--plant" aria-hidden="true">
          <img src={avatarSrc} alt="device avatar" />
        </div>
        <div className="device-card__plant-meta">
          <div className="device-card__title device-card__title--compact">{displayName}</div>
          <div className="device-card__subtitle device-card__subtitle--compact">{device.device_id}</div>
          <StatusBadge isOnline={device.is_online} />
        </div>
        {onEdit && (
          <button
            type="button"
            className="device-card__edit device-card__edit--ghost"
            onClick={handleEdit}
            aria-label="Р РµРґР°РєС‚РёСЂРѕРІР°С‚СЊ"
          >
            ??
          </button>
        )}
      </div>
      <div className="device-card__plant-metrics">
        <div className="device-chip">
          <span className="device-chip__label">РџРѕС‡РІР°</span>
          <span className="device-chip__value">{formatSensorValue(device.soil_moisture)}</span>
        </div>
        <div className="device-chip">
          <span className="device-chip__label">Р’РѕР·РґСѓС…</span>
          <span className="device-chip__value">{formatSensorValue(device.air_humidity)}</span>
        </div>
        <div className="device-chip">
          <span className="device-chip__label">T</span>
          <span className="device-chip__value">{formatSensorValue(device.air_temperature)}</span>
        </div>
      </div>
    </div>
  );

  if (variant === 'plant') {
    return renderPlant();
  }

  return renderDefault();
}

export default DeviceCard;

