import React from 'react';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import { resolveDeviceAsset } from './assets';
import { formatSensorValue } from '../../utils/formatters';
import SensorPill from '../ui/SensorPill';
import Button from '../ui/Button';
import './DeviceCard.css';

// Translitem: DeviceCard - komponent otobrazheniya ustrojstva s dvumya variantami (default dlya stranicy ustrojstv, plant dlya vlozheniya v kartochku rastenija).
// Translitem: Ispolzuetsya v AppDevices.jsx; pokazyvaet avatar ustrojstva, status online/offline, nazvanie, firmware i osnovnye sensornye znacheniya.
function StatusBadge({ isOnline }) {
  return (
    <div className="device-card__status">
      <span className={`status-dot ${isOnline ? 'is-online' : 'is-offline'}`} aria-hidden="true" />
      {isOnline ? 'Онлайн' : 'Оффлайн'}
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
  const displayName = device.name || 'Устройство';

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
        <Button type="button" variant="ghost" size="sm" className="device-card__edit" onClick={handleEdit} aria-label="Редактировать">
          ?
        </Button>
      </div>

      <div className="device-card__body">
        <div className="device-card__avatar" aria-hidden="true">
          <img src={avatarSrc} alt="device avatar" />
        </div>
        <div className="device-card__info">
          <div className="device-card__fw">Прошивка: {firmware}</div>
          <div className="device-card__metrics">
            <SensorPill
              label="Влажн. почвы, %"
              value={formatSensorValue(device.soil_moisture)}
              onClick={device.device_id ? () => openSensorStats({ deviceId: device.device_id, metric: 'soil_moisture' }) : undefined}
              disabled={!device.device_id}
            />
            <SensorPill
              label="Влажн. воздуха, %"
              value={formatSensorValue(device.air_humidity)}
              onClick={device.device_id ? () => openSensorStats({ deviceId: device.device_id, metric: 'air_humidity' }) : undefined}
              disabled={!device.device_id}
            />
            <SensorPill
              label="T, °C"
              value={formatSensorValue(device.air_temperature)}
              onClick={device.device_id ? () => openSensorStats({ deviceId: device.device_id, metric: 'air_temperature' }) : undefined}
              disabled={!device.device_id}
            />
            <SensorPill
              label="Насос"
              value={wateringSpeed ? `Скорость: ${wateringSpeed} л/ч` : 'не задано'}
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
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="device-card__edit"
            onClick={handleEdit}
            aria-label="Редактировать"
          >
            ??
          </Button>
        )}
      </div>
      <div className="device-card__plant-metrics">
        <div className="device-chip">
          <span className="device-chip__label">Почва</span>
          <span className="device-chip__value">{formatSensorValue(device.soil_moisture)}</span>
        </div>
        <div className="device-chip">
          <span className="device-chip__label">Воздух</span>
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
