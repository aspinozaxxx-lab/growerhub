import React from 'react';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import { resolveDeviceAsset } from './assets';
import SensorPill from '../ui/sensor-pill/SensorPill';
import Button from '../ui/Button';
import Surface from '../ui/Surface';
import { Title, Text } from '../ui/Typography';
import './DeviceCard.css';

const SENSOR_KIND_MAP = {
  SOIL_MOISTURE: 'soil_moisture',
  AIR_TEMPERATURE: 'air_temperature',
  AIR_HUMIDITY: 'air_humidity',
};

const SENSOR_TITLE_MAP = {
  SOIL_MOISTURE: 'Влажность почвы',
  AIR_TEMPERATURE: 'Температура воздуха',
  AIR_HUMIDITY: 'Влажность воздуха',
};

// Translitem: DeviceCard - komponent otobrazheniya ustrojstva s fokusom na sensory/pumpy i privyazki.
function StatusBadge({ isOnline }) {
  return (
    <div className="device-card__status">
      <span className={`status-dot ${isOnline ? 'is-online' : 'is-offline'}`} aria-hidden="true" />
      {isOnline ? 'Онлайн' : 'Оффлайн'}
    </div>
  );
}

function buildSensorTitle(sensor) {
  const base = sensor?.label || SENSOR_TITLE_MAP[sensor?.type] || sensor?.type || 'Датчик';
  if (sensor?.channel === null || sensor?.channel === undefined) {
    return base;
  }
  return `${base} · канал ${sensor.channel}`;
}

function buildPumpTitle(pump) {
  const base = pump?.label || 'Насос';
  if (pump?.channel === null || pump?.channel === undefined) {
    return base;
  }
  return `${base} · канал ${pump.channel}`;
}

function DeviceCard({ device, onEdit }) {
  const { openSensorStats } = useSensorStatsContext();
  const firmware = device.firmware_version || device.current_version || 'n/a';
  const avatarKey = 'grovika_mini';
  const avatarSrc = resolveDeviceAsset(avatarKey);
  const displayName = device.name || 'Устройство';

  const sensors = Array.isArray(device.sensors) ? device.sensors : [];
  const pumps = Array.isArray(device.pumps) ? device.pumps : [];

  const handleEdit = () => {
    if (onEdit) {
      onEdit(device);
    }
  };

  const handleSensorStats = (sensor) => {
    const kind = SENSOR_KIND_MAP[sensor.type] || 'soil_moisture';
    openSensorStats({
      mode: 'sensor',
      sensorId: sensor.id,
      metric: kind,
      title: buildSensorTitle(sensor),
      subtitle: displayName,
    });
  };

  return (
    <Surface variant="card" padding="md" className="device-card">
      <div className="device-card__header">
        <div>
          <Title level={3} className="device-card__title">{displayName}</Title>
          <Text tone="muted" className="device-card__subtitle">{device.device_id}</Text>
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
          <Text tone="muted" className="device-card__fw">Прошивка: {firmware}</Text>
        </div>
      </div>

      <div className="device-card__section">
        <div className="device-card__section-title">Датчики</div>
        {sensors.length === 0 && <div className="device-card__empty">Нет датчиков</div>}
        {sensors.map((sensor) => {
          const kind = SENSOR_KIND_MAP[sensor.type] || 'soil_moisture';
          const boundPlants = Array.isArray(sensor.bound_plants) ? sensor.bound_plants : [];
          return (
            <div key={sensor.id} className="device-card__item">
              <div className="device-card__item-main">
                <div className="device-card__item-title">{buildSensorTitle(sensor)}</div>
                <SensorPill
                  kind={kind}
                  value={sensor.last_value}
                  onClick={() => handleSensorStats(sensor)}
                />
              </div>
              <div className="device-card__bindings">
                {boundPlants.length === 0 && <span className="device-card__binding-empty">Нет привязок</span>}
                {boundPlants.map((plant) => (
                  <span key={plant.id} className="device-card__plant-pill">{plant.name}</span>
                ))}
              </div>
            </div>
          );
        })}
      </div>

      <div className="device-card__section">
        <div className="device-card__section-title">Насосы</div>
        {pumps.length === 0 && <div className="device-card__empty">Нет насосов</div>}
        {pumps.map((pump) => {
          const boundPlants = Array.isArray(pump.bound_plants) ? pump.bound_plants : [];
          const statusLabel = pump.is_running === null || pump.is_running === undefined
            ? 'Нет данных'
            : pump.is_running
              ? 'Выполняется'
              : 'Остановлен';
          return (
            <div key={pump.id} className="device-card__item">
              <div className="device-card__item-main">
                <div className="device-card__item-title">{buildPumpTitle(pump)}</div>
                <div className="device-card__item-status">{statusLabel}</div>
              </div>
              <div className="device-card__bindings">
                {boundPlants.length === 0 && <span className="device-card__binding-empty">Нет привязок</span>}
                {boundPlants.map((plant) => {
                  const rate = plant.rate_ml_per_hour;
                  const label = rate ? `${plant.name} · ${rate} мл/ч` : plant.name;
                  return (
                    <span key={plant.id} className="device-card__plant-pill">{label}</span>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </Surface>
  );
}

export default DeviceCard;
