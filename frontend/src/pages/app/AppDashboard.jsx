import React from "react";
import { Link, useNavigate } from "react-router-dom";
import { useDashboardData } from "../../features/dashboard/useDashboardData";
import { useSensorStatsContext } from "../../features/sensors/SensorStatsContext";
import { useWateringSidebar } from "../../features/watering/WateringSidebarContext";
import { formatSensorValue } from "../../utils/formatters";
import PlantAvatar from "../../components/plant-avatar/PlantAvatar";
import { getStageFromPlantAgeDays } from "../../components/plant-avatar/plantStageFromAge";
import SensorPill from "../../components/ui/SensorPill";
import "./AppDashboard.css";

const MS_IN_DAY = 24 * 60 * 60 * 1000;

function formatAge(plantedAt) {
  if (!plantedAt) {
    return "Дата неизвестна";
  }
  const date = new Date(plantedAt);
  if (Number.isNaN(date.getTime())) {
    return "Дата неизвестна";
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
    <SensorPill
      label={label}
      value={display}
      onClick={handleClick}
      isHighlight={highlight}
      disabled={!deviceId || !metric}
    />
  );
}

function formatRemaining(seconds) {
  if (seconds === null || seconds === undefined) {
    return "";
  }
  const clamped = Math.max(0, Math.ceil(seconds));
  const minutes = Math.floor(clamped / 60);
  const secs = clamped % 60;
  const parts = [];
  if (minutes > 0) {
    parts.push(`${minutes} мин`);
  }
  parts.push(`${secs} с`);
  return parts.join(" ");
}

function PlantCard({ plant, onOpenStats, onOpenWatering, wateringStatus, onOpenJournal }) {
  const primaryDevice = plant?.devices?.[0]; // TODO: zamenit' na vybor konkretnogo ustrojstva, esli ih neskol'ko.
  const [remainingSeconds, setRemainingSeconds] = React.useState(null);

  React.useEffect(() => {
    if (!wateringStatus || !wateringStatus.startTime || !wateringStatus.duration) {
      setRemainingSeconds(null);
      return undefined;
    }
    const startTs = new Date(wateringStatus.startTime).getTime();
    if (!Number.isFinite(startTs)) {
      setRemainingSeconds(null);
      return undefined;
    }
    const durationMs = wateringStatus.duration * 1000;
    const updateRemaining = () => {
      const diffMs = startTs + durationMs - Date.now();
      const next = diffMs <= 0 ? 0 : Math.ceil(diffMs / 1000);
      setRemainingSeconds(next);
    };
    updateRemaining();
    const interval = window.setInterval(updateRemaining, 1000);
    return () => {
      window.clearInterval(interval);
    };
  }, [wateringStatus]);

  // Ocenochnoe chislo dnej s posadki dlya vibora stadii avatara
  const ageDays = React.useMemo(() => {
    if (!plant?.planted_at) return null;
    const plantedDate = new Date(plant.planted_at);
    if (Number.isNaN(plantedDate.getTime())) {
      return null;
    }
    const diff = Date.now() - plantedDate.getTime();
    return Math.max(0, Math.floor(diff / MS_IN_DAY));
  }, [plant?.planted_at]);

  // Stadiya opredelyaetsya avtomaticheski po vozrastu; v budushem mozhet idti iz API
  const stageId = getStageFromPlantAgeDays(ageDays);

  const showWateringBadge = wateringStatus && remainingSeconds !== null && remainingSeconds > 0;

  const handleOpenWatering = () => {
    if (primaryDevice?.device_id && onOpenWatering) {
      onOpenWatering({ deviceId: primaryDevice.device_id, plantId: plant.id });
    }
  };

  return (
    <div className="plant-card">
      <div className="plant-card__header">
        <div>
          <div className="plant-card__name">{plant.name}</div>
          <div className="plant-card__group">
            {plant.plant_group ? plant.plant_group.name : "Без группы"}
          </div>
        </div>
        <div className="plant-card__age">{formatAge(plant.planted_at)}</div>
      </div>

      {primaryDevice ? (
        <div className="plant-card__body">
          <div className="plant-card__avatar" aria-hidden="true">
            <PlantAvatar
              plantType="flowering"
              // Stadiya teper' nujna tol'ko dlya vybora staticheskogo svg
              stage={stageId}
              variant="card"
              size="md"
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
              label="Влажн. воздуха, %"
              value={primaryDevice.air_humidity}
              metric="air_humidity"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
            />
            <MetricPill
              label="Влажн. почвы, %"
              value={primaryDevice.soil_moisture}
              metric="soil_moisture"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
            />
            <MetricPill
              label="Полив"
              value={primaryDevice.is_watering ? "Выполняется" : "Нет"}
              metric="watering"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
              highlight={Boolean(primaryDevice.is_watering)}
            />
            {showWateringBadge && (
              <div className="plant-card__watering-badge">
                Идёт полив · осталось {formatRemaining(remainingSeconds)}
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="plant-card__empty">Нет подключённых устройств</div>
      )}

      <div className="plant-card__footer">
        <div className="plant-card__actions">
          <button
            type="button"
            className="plant-card__action-btn"
            onClick={handleOpenWatering}
            disabled={!primaryDevice}
          >
            Полив
          </button>
          <button
            type="button"
            className="plant-card__action-btn"
            onClick={() => onOpenJournal?.(plant.id)}
          >
            Журнал
          </button>
          <Link className="plant-card__link" to="/app/plants">
            Перейти →
          </Link>
        </div>
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
            {device.is_online ? "Онлайн" : "Оффлайн"}
            <span className={`status-dot ${device.is_online ? "is-online" : "is-offline"}`} aria-hidden="true" />
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
          value={device.is_watering ? "Выполняется" : "Нет"}
          metric="watering"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
          highlight={Boolean(device.is_watering)}
        />
      </div>

      {/* TODO: dobavit' knopku privyazki ustrojstva k rasteniyu */}
    </div>
  );
}

function AppDashboard() {
  const { plants, devices, freeDevices, isLoading, error } = useDashboardData();
  const { openSensorStats } = useSensorStatsContext();
  const { openWateringSidebar, wateringByDevice } = useWateringSidebar();
  const navigate = useNavigate();

  const handleOpenJournal = (plantId) => {
    navigate(`/app/plants/${plantId}/journal`);
  };

  return (
    <div className="dashboard">
      {isLoading && <div className="dashboard__state">Загрузка...</div>}
      {error && <div className="dashboard__state dashboard__state--error">{error}</div>}

      {!isLoading && !error && plants.length === 0 && freeDevices.length === 0 && (
        <div className="dashboard__state">Пока нет данных. Добавьте растения и подключите устройства.</div>
      )}

      {!isLoading && !error && plants.length > 0 && (
        <section className="dashboard-section">
          <div className="dashboard-section__header">
            <h2>Растения</h2>
          </div>
          <div className="cards-grid">
            {plants.map((plant) => {
              const deviceKey = plant?.devices?.[0]?.device_id;
              return (
                <PlantCard
                  key={plant.id}
                  plant={plant}
                  onOpenStats={openSensorStats}
                  onOpenWatering={openWateringSidebar}
                  onOpenJournal={handleOpenJournal}
                  wateringStatus={deviceKey ? wateringByDevice[deviceKey] : null}
                />
              );
            })}
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
