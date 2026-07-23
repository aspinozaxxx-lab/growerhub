import React from 'react';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import { resolveDeviceAsset } from './assets';
import SensorPill from '../ui/sensor-pill/SensorPill';
import Button from '../ui/Button';
import Surface from '../ui/Surface';
import { Title, Text } from '../ui/Typography';
import WateringInProgressBanner from '../watering/WateringInProgressBanner';
import usePumpWateringStatus from '../../features/watering/usePumpWateringStatus';
import './DeviceCard.css';
import { translateApp } from '../../locales/i18n';

const SENSOR_KIND_MAP = {
  SOIL_MOISTURE: 'soil_moisture',
  AIR_TEMPERATURE: 'air_temperature',
  AIR_HUMIDITY: 'air_humidity',
};

const SENSOR_TITLE_MAP = {
  SOIL_MOISTURE: translateApp("Влажность почвы"),
  AIR_TEMPERATURE: translateApp("Температура воздуха"),
  AIR_HUMIDITY: translateApp("Влажность воздуха"),
};

// Translitem: DeviceCard - komponent otobrazheniya ustrojstva s fokusom na sensory/pumpy i privyazki.
function StatusBadge({ isOnline }) {
  return (
    <div className="device-card__status">
      <span className={`status-dot ${isOnline ? 'is-online' : 'is-offline'}`} aria-hidden="true" />
      {isOnline ? translateApp("Онлайн") : translateApp("Оффлайн")}
    </div>
  );
}

function buildSensorTitle(sensor) {
  const base = sensor?.label || SENSOR_TITLE_MAP[sensor?.type] || sensor?.type || translateApp("Датчик");
  if (sensor?.channel === null || sensor?.channel === undefined) {
    return base;
  }
  return translateApp("{{value1}} · канал {{value2}}", { value1: base, value2: sensor.channel });
}

function buildPumpTitle(pump) {
  const base = pump?.label || translateApp("Насос");
  if (pump?.channel === null || pump?.channel === undefined) {
    return base;
  }
  return translateApp("{{value1}} · канал {{value2}}", { value1: base, value2: pump.channel });
}

function DevicePumpRow({ pump, isOnline }) {
  const isWateringFallback = pump?.is_running === true;
  const { remainingSeconds, isRunning, stop } = usePumpWateringStatus(pump?.id, {
    enabled: Boolean(pump?.id && isWateringFallback),
  });
  const hasStatus = isRunning !== null && isRunning !== undefined;
  const isWatering = hasStatus ? isRunning : isWateringFallback;
  const statusLabel = hasStatus
    ? (isRunning ? translateApp("Выполняется") : translateApp("Остановлен"))
    : (pump.is_running === null || pump.is_running === undefined
      ? translateApp("Нет данных")
      : pump.is_running
        ? translateApp("Выполняется")
        : translateApp("Остановлен"));
  const boundPlants = Array.isArray(pump.bound_plants) ? pump.bound_plants : [];

  return (
    <div className="device-card__item">
      <div className="device-card__item-main">
        <div className="device-card__item-title">{buildPumpTitle(pump)}</div>
        <div className="device-card__item-status">
          {!isOnline ? (
            <SensorPill
              kind="watering"
              value={isWatering}
              isOffline
            />
          ) : isWatering ? (
            <WateringInProgressBanner
              isWatering={isWatering}
              remainingSeconds={remainingSeconds}
              onStop={stop}
            />
          ) : (
            statusLabel
          )}
        </div>
      </div>
      <div className="device-card__bindings">
        {boundPlants.length === 0 && <span className="device-card__binding-empty">{translateApp("Нет привязок")}</span>}
        {boundPlants.map((plant) => {
          const rate = plant.rate_ml_per_hour;
          const label = rate ? translateApp("{{value1}} · {{value2}} мл/ч", { value1: plant.name, value2: rate }) : plant.name;
          return (
            <span key={plant.id} className="device-card__plant-pill">{label}</span>
          );
        })}
      </div>
    </div>
  );
}

function DeviceCard({ device, onEdit }) {
  const { openSensorStats } = useSensorStatsContext();
  const firmware = device.firmware_version || device.current_version || 'n/a';
  const avatarKey = 'grovika_mini';
  const avatarSrc = resolveDeviceAsset(avatarKey);
  const displayName = device.name || translateApp("Устройство");

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
        <Button type="button" variant="ghost" size="sm" className="device-card__edit" onClick={handleEdit} aria-label={translateApp("Редактировать")}>
          <svg className="device-card__edit-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M4 20h4l10-10-4-4-10 10v4zM14 6l4 4"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </Button>
      </div>

      <div className="device-card__body">
        <div className="device-card__avatar" aria-hidden="true">
          <img src={avatarSrc} alt="device avatar" />
        </div>
        <div className="device-card__info">
          <Text tone="muted" className="device-card__fw">{translateApp("Прошивка:")}{firmware}</Text>
        </div>
      </div>

      <div className="device-card__section">
        <div className="device-card__section-title">{translateApp("Датчики")}</div>
        {sensors.length === 0 && <div className="device-card__empty">{translateApp("Нет датчиков")}</div>}
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
                  status={sensor.status}
                  isOffline={!device.is_online}
                  onClick={() => handleSensorStats(sensor)}
                />
              </div>
              <div className="device-card__bindings">
                {boundPlants.length === 0 && <span className="device-card__binding-empty">{translateApp("Нет привязок")}</span>}
                {boundPlants.map((plant) => (
                  <span key={plant.id} className="device-card__plant-pill">{plant.name}</span>
                ))}
              </div>
            </div>
          );
        })}
      </div>

      <div className="device-card__section">
        <div className="device-card__section-title">{translateApp("Насосы")}</div>
        {pumps.length === 0 && <div className="device-card__empty">{translateApp("Нет насосов")}</div>}
        {pumps.map((pump) => (
          <DevicePumpRow key={pump.id} pump={pump} isOnline={Boolean(device.is_online)} />
        ))}
      </div>
    </Surface>
  );
}

export default DeviceCard;
