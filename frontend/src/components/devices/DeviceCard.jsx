import React from 'react';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import { resolveDeviceAsset } from './assets';
import { formatSensorValue } from '../../utils/formatters';
import SensorPill from '../ui/SensorPill';
import './DeviceCard.css';

// Translitem: DeviceCard - komponent otobrazheniya ustrojstva s dvumya variantami (default dlya stranicy ustrojstv, plant dlya vlozheniya v kartochku rastenija).
// Translitem: Ispolzuetsya v AppDevices.jsx; pokazyvaet avatar ustrojstva, status online/offline, nazvanie, firmware i osnovnye sensornye znacheniya.
function StatusBadge({ isOnline }) {
  return (
    <div className="device-card__status">
      <span className={`status-dot ${isOnline ? 'is-online' : 'is-offline'}`} aria-hidden="true" />
      {isOnline ? '\u041e\u043d\u043b\u0430\u0439\u043d' : '\u041e\u0444\u0444\u043b\u0430\u0439\u043d'}
    </div>
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
  const displayName = device.name || '\u0423\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e';

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
        <button type="button" className="device-card__edit" onClick={handleEdit} aria-label="\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u0442\u044c">
          \u270f
        </button>
      </div>

      <div className="device-card__body">
        <div className="device-card__avatar" aria-hidden="true">
          <img src={avatarSrc} alt="device avatar" />
        </div>
        <div className="device-card__info">
          <div className="device-card__fw">\u041f\u0440\u043e\u0448\u0438\u0432\u043a\u0430: {firmware}</div>
          <div className="device-card__metrics">
            <SensorPill
              label="\u0412\u043b\u0430\u0436\u043d. \u043f\u043e\u0447\u0432\u044b, %"
              value={formatSensorValue(device.soil_moisture)}
              onClick={device.device_id ? () => openSensorStats({ deviceId: device.device_id, metric: 'soil_moisture' }) : undefined}
              disabled={!device.device_id}
            />
            <SensorPill
              label="\u0412\u043b\u0430\u0436\u043d. \u0432\u043e\u0437\u0434\u0443\u0445\u0430, %"
              value={formatSensorValue(device.air_humidity)}
              onClick={device.device_id ? () => openSensorStats({ deviceId: device.device_id, metric: 'air_humidity' }) : undefined}
              disabled={!device.device_id}
            />
            <SensorPill
              label="T, \u00b0C"
              value={formatSensorValue(device.air_temperature)}
              onClick={device.device_id ? () => openSensorStats({ deviceId: device.device_id, metric: 'air_temperature' }) : undefined}
              disabled={!device.device_id}
            />
            <SensorPill
              label="\u041d\u0430\u0441\u043e\u0441"
              value={wateringSpeed ? `\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c: ${wateringSpeed} \u043b/\u0447` : '\u043d\u0435 \u0437\u0430\u0434\u0430\u043d\u043e'}
              onClick={handleEdit}
            />
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
            aria-label="\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u0442\u044c"
          >
            ??
          </button>
        )}
      </div>
      <div className="device-card__plant-metrics">
        <div className="device-chip">
          <span className="device-chip__label">\u041f\u043e\u0447\u0432\u0430</span>
          <span className="device-chip__value">{formatSensorValue(device.soil_moisture)}</span>
        </div>
        <div className="device-chip">
          <span className="device-chip__label">\u0412\u043e\u0437\u0434\u0443\u0445</span>
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
