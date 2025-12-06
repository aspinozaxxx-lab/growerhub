import React from 'react';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import pumpIcon from '../../assets/plant-pot.svg';
import { formatSensorValue } from '../../utils/formatters';
import './DeviceCard.css';

function StatusBadge({ isOnline }) {
  return (
    <div className="device-card__status">
      <span className={`status-dot ${isOnline ? 'is-online' : 'is-offline'}`} aria-hidden="true" />
      {isOnline ? 'Онлайн' : 'Оффлайн'}
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

function DeviceCard({ device, onEdit }) {
  const { openSensorStats } = useSensorStatsContext();
  const firmware = device.firmware_version || device.current_version || 'n/a';
  const wateringSpeed = device.watering_speed_lph;

  const handleEdit = () => {
    if (onEdit) {
      onEdit(device);
    }
  };

  return (
    <div className="device-card">
      <div className="device-card__header">
        <div>
          <div className="device-card__title">{device.name || 'Устройство'}</div>
          <div className="device-card__subtitle">{device.device_id}</div>
          <StatusBadge isOnline={device.is_online} />
        </div>
        <button type="button" className="device-card__edit" onClick={handleEdit} aria-label="Редактировать">
          ✏️
        </button>
      </div>

      <div className="device-card__body">
        <div className="device-card__avatar" aria-hidden="true">
          <img src={pumpIcon} alt="device avatar" />
        </div>
        <div className="device-card__info">
          <div className="device-card__fw">Прошивка: {firmware}</div>
          <div className="device-card__metrics">
            <MetricPill
              label="Влажн. почвы, %"
              value={device.soil_moisture}
              metric="soil_moisture"
              deviceId={device.device_id}
              onOpenStats={openSensorStats}
            />
            <MetricPill
              label="Влажн. воздуха, %"
              value={device.air_humidity}
              metric="air_humidity"
              deviceId={device.device_id}
              onOpenStats={openSensorStats}
            />
            <MetricPill
              label="T, °C"
              value={device.air_temperature}
              metric="air_temperature"
              deviceId={device.device_id}
              onOpenStats={openSensorStats}
            />
            <button type="button" className="metric-pill pump-pill" onClick={handleEdit}>
              <span className="metric-pill__label">Насос</span>
              <span className="metric-pill__value">
                {wateringSpeed ? `Скорость: ${wateringSpeed} л/ч` : 'не задано'}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default DeviceCard;
