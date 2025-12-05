import React from 'react';
import { Link } from 'react-router-dom';
import { useDashboardData } from '../../features/dashboard/useDashboardData';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import { formatSensorValue } from '../../utils/formatters';
import plantPot from '../../assets/plant-pot.svg';
import './AppDashboard.css';

const MS_IN_DAY = 24 * 60 * 60 * 1000;

function formatAge(plantedAt) {
  if (!plantedAt) {
    return 'Возраст неизвестен';
  }
  const date = new Date(plantedAt);
  if (Number.isNaN(date.getTime())) {
    return 'Возраст неизвестен';
  }
  const days = Math.max(0, Math.floor((Date.now() - date.getTime()) / MS_IN_DAY));
  return `${days} дн.`;
}

function MetricPill({ label, value, metric, deviceId, onOpenStats, highlight = false }) {
  const display = formatSensorValue(value);
  const handleClick = () => {
    if (deviceId && metric) {
      onOpenStats({ deviceId, metric });
    }
  };
  return (
    <button
      type="button"
      className={`metric-pill ${highlight ? 'is-highlight' : ''}`}
      onClick={handleClick}
      disabled={!deviceId || !metric}
    >
      <span className="metric-pill__label">{label}</span>
      <span className="metric-pill__value">{display}</span>
    </button>
  );
}

function PlantCard({ plant, onOpenStats }) {
  const primaryDevice = plant?.devices?.[0]; // TODO: заменить на выбор конкретного устройства, если их несколько.
  return (
    <div className="plant-card">
      <div className="plant-card__header">
        <div>
          <div className="plant-card__name">{plant.name}</div>
          <div className="plant-card__group">
            {plant.plant_group ? plant.plant_group.name : 'Без группы'}
          </div>
        </div>
        <div className="plant-card__age">{formatAge(plant.planted_at)}</div>
      </div>

      {primaryDevice ? (
        <div className="plant-card__body">
          <div className="plant-card__avatar" aria-hidden="true">
            <img
              src={plant.avatarUrl || plantPot}
              alt={plant.name}
              loading="lazy"
            />
          </div>
          <div className="plant-card__metrics">
            <MetricPill
              label="T, °C"
              value={primaryDevice.air_temperature}
              metric="air_temperature"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
            />
            <MetricPill
              label="Вл.возд, %"
              value={primaryDevice.air_humidity}
              metric="air_humidity"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
            />
            <MetricPill
              label="Вл.почв, %"
              value={primaryDevice.soil_moisture}
              metric="soil_moisture"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
            />
            <MetricPill
              label="Полив"
              value={primaryDevice.is_watering ? 'Выполняется' : 'Нет'}
              metric="watering"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
              highlight={Boolean(primaryDevice.is_watering)}
            />
          </div>
        </div>
      ) : (
        <div className="plant-card__empty">Нет подключённых устройств</div>
      )}

      <div className="plant-card__footer">
        <Link className="plant-card__link" to="/app/plants">
          Журнал →
        </Link>
      </div>
    </div>
  );
}

function FreeDeviceCard({ device, onOpenStats }) {
  return (
    <div className="free-device-card">
      <div className="free-device-card__header">
        <div>
          <div className="free-device-card__name">{device.name || device.device_id}</div>
          <div className="free-device-card__id">{device.device_id}</div>
          <div className="free-device-card__status">
            {device.is_online ? 'Онлайн' : 'Оффлайн'}
            <span className={`status-dot ${device.is_online ? 'is-online' : 'is-offline'}`} aria-hidden="true" />
          </div>
        </div>
        <div className="free-device-card__tag">Не привязано</div>
      </div>

      <div className="free-device-card__metrics">
        <MetricPill
          label="T, °C"
          value={device.air_temperature}
          metric="air_temperature"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
        />
        <MetricPill
          label="Влажн. воздуха, %"
          value={device.air_humidity}
          metric="air_humidity"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
        />
        <MetricPill
          label="Влажн. почвы, %"
          value={device.soil_moisture}
          metric="soil_moisture"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
        />
        <MetricPill
          label="Полив"
          value={device.is_watering ? 'Выполняется' : 'Нет'}
          metric="watering"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
          highlight={Boolean(device.is_watering)}
        />
      </div>

      {/* TODO: добавить кнопку привязки устройства к растению */}
    </div>
  );
}

function AppDashboard() {
  const { plants, devices, freeDevices, isLoading, error } = useDashboardData();
  const { openSensorStats } = useSensorStatsContext();

  return (
    <div className="dashboard">
      {isLoading && <div className="dashboard__state">Загрузка...</div>}
      {error && <div className="dashboard__state dashboard__state--error">{error}</div>}

      {!isLoading && !error && plants.length === 0 && freeDevices.length === 0 && (
        <div className="dashboard__state">Пока здесь пусто. Добавьте устройство или растение.</div>
      )}

      {!isLoading && !error && plants.length > 0 && (
        <section className="dashboard-section">
          <div className="dashboard-section__header">
            <h2>Растения</h2>
          </div>
          <div className="cards-grid">
            {plants.map((plant) => (
              <PlantCard key={plant.id} plant={plant} onOpenStats={openSensorStats} />
            ))}
          </div>
        </section>
      )}

      {!isLoading && !error && freeDevices.length > 0 && (
        <section className="dashboard-section">
          <div className="dashboard-section__header">
            <h2>Свободные устройства</h2>
            <p className="dashboard-section__subtitle">Не привязаны к растениям</p>
          </div>
          <div className="cards-grid">
            {freeDevices.map((device) => (
              <FreeDeviceCard key={device.id} device={device} onOpenStats={openSensorStats} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

export default AppDashboard;
