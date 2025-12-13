import React from "react";
import { Link, useNavigate } from "react-router-dom";
import { useDashboardData } from "../../features/dashboard/useDashboardData";
import { useSensorStatsContext } from "../../features/sensors/SensorStatsContext";
import { useWateringSidebar } from "../../features/watering/WateringSidebarContext";
import { formatSensorValue } from "../../utils/formatters";
import PlantAvatar from "../../components/plant-avatar/PlantAvatar";
import { getStageFromPlantAgeDays } from "../../components/plant-avatar/plantStageFromAge";
import SensorPill from "../../components/ui/SensorPill";
import Button from "../../components/ui/Button";
import Surface from "../../components/ui/Surface";
import { Title, Text } from "../../components/ui/Typography";
import AppPageState from "../../components/layout/AppPageState";
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

function MetricPill({ kind, value, metric, deviceId, onOpenStats, highlight = false }) {
  const handleClick = () => {
    if (deviceId && metric) {
      onOpenStats({ deviceId, metric });
    }
  };
  return (
    <SensorPill
      kind={kind}
      value={value}
      onClick={handleClick}
      highlight={highlight}
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
    <div className="dashboard-plant-card">
      <div className="dashboard-plant-card__header">
        <div>
          <Title level={3} className="dashboard-plant-card__name">{plant.name}</Title>
          <Text tone="muted" className="dashboard-plant-card__group">
            {plant.plant_group ? plant.plant_group.name : "Без группы"}
          </Text>
        </div>
        <div className="dashboard-plant-card__age">{formatAge(plant.planted_at)}</div>
      </div>

      {primaryDevice ? (
        <div className="dashboard-plant-card__body">
          <div className="dashboard-plant-card__avatar" aria-hidden="true">
            <PlantAvatar
              plantType="flowering"
              // Stadiya teper' nujna tol'ko dlya vybora staticheskogo svg
              stage={stageId}
              variant="card"
              size="md"
            />
          </div>
          <div className="dashboard-plant-card__metrics">
            <MetricPill
              kind="air_temperature"
              value={primaryDevice.air_temperature}
              metric="air_temperature"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
            />
            <MetricPill
              kind="air_humidity"
              value={primaryDevice.air_humidity}
              metric="air_humidity"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
            />
            <MetricPill
              kind="soil_moisture"
              value={primaryDevice.soil_moisture}
              metric="soil_moisture"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
            />
            <MetricPill
              kind="watering"
              value={primaryDevice.is_watering}
              metric="watering"
              deviceId={primaryDevice.device_id}
              onOpenStats={onOpenStats}
              highlight={Boolean(primaryDevice.is_watering)}
            />
            {showWateringBadge && (
              <div className="dashboard-plant-card__watering-badge">
                Идёт полив · осталось {formatRemaining(remainingSeconds)}
              </div>
            )}
          </div>
        </div>
      ) : (
          <Text tone="muted" className="dashboard-plant-card__empty">Нет подключённых устройств</Text>
      )}

      <div className="dashboard-plant-card__footer">
        <div className="dashboard-plant-card__actions">
          <Button
            type="button"
            variant="secondary"
            onClick={handleOpenWatering}
            disabled={!primaryDevice}
          >
            Полив
          </Button>
          <Button
            type="button"
            variant="secondary"
            onClick={() => onOpenJournal?.(plant.id)}
          >
            Журнал
          </Button>
          <Link className="dashboard-plant-card__link" to="/app/plants">
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
            <span className={`dashboard-status-dot ${device.is_online ? "is-online" : "is-offline"}`} aria-hidden="true" />
          </div>
        </div>
        <div className="free-device-card__tag">Не привязано</div>
      </div>

      <div className="free-device-card__metrics">
        <MetricPill
          kind="air_temperature"
          value={device.air_temperature}
          metric="air_temperature"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
        />
        <MetricPill
          kind="air_humidity"
          value={device.air_humidity}
          metric="air_humidity"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
        />
        <MetricPill
          kind="soil_moisture"
          value={device.soil_moisture}
          metric="soil_moisture"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
        />
        <MetricPill
          kind="watering"
          value={device.is_watering}
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
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <AppPageState kind="error" title={error} />}

      {!isLoading && !error && plants.length === 0 && freeDevices.length === 0 && (
        <AppPageState kind="empty" title="Пока нет данных. Добавьте растения и подключите устройства." />
      )}

      {!isLoading && !error && plants.length > 0 && (
        <Surface variant="section" padding="md" className="dashboard-section">
          <div className="dashboard-section__header">
            <Title level={2}>Растения</Title>
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
        </Surface>
      )}

      {!isLoading && !error && freeDevices.length > 0 && (
        <Surface variant="section" padding="md" className="dashboard-section">
          <div className="dashboard-section__header">
            <Title level={2}>Свободные устройства</Title>
            <Text tone="muted" className="dashboard-section__subtitle">Не привязаны к растениям</Text>
          </div>
          <div className="cards-grid">
            {freeDevices.map((device) => (
              <FreeDeviceCard key={device.id} device={device} onOpenStats={openSensorStats} />
            ))}
          </div>
        </Surface>
      )}
    </div>
  );
}

export default AppDashboard;
