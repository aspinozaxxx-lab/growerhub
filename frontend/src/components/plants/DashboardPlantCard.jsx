import React from 'react';
import { Link } from 'react-router-dom';
import PlantAvatar from '../plant-avatar/PlantAvatar';
import { getStageFromPlantAgeDays } from '../plant-avatar/plantStageFromAge';
import { parseBackendTimestamp } from '../../utils/formatters';
import SensorPill from '../ui/sensor-pill/SensorPill';
import Button from '../ui/Button';
import Surface from '../ui/Surface';
import { Title, Text } from '../ui/Typography';
import './DashboardPlantCard.css';

const MS_IN_DAY = 24 * 60 * 60 * 1000;

function formatAge(plantedAt) {
  if (!plantedAt) {
    return 'Дата неизвестна';
  }
  const date = new Date(plantedAt);
  if (Number.isNaN(date.getTime())) {
    return 'Дата неизвестна';
  }
  const days = Math.max(0, Math.floor((Date.now() - date.getTime()) / MS_IN_DAY));
  return `${days} дн.`;
}

function formatRemaining(seconds) {
  if (seconds === null || seconds === undefined) {
    return '';
  }
  const clamped = Math.max(0, Math.ceil(seconds));
  const minutes = Math.floor(clamped / 60);
  const secs = clamped % 60;
  const parts = [];
  if (minutes > 0) {
    parts.push(`${minutes} мин`);
  }
  parts.push(`${secs} с`);
  return parts.join(' ');
}

function DashboardPlantCard({
  plant,
  onOpenStats,
  onOpenWatering,
  wateringStatus,
  onOpenJournal,
}) {
  const primaryDevice = plant?.devices?.[0]; // TODO: zamenit' na vybor konkretnogo ustrojstva, esli ih neskol'ko.
  const [remainingSeconds, setRemainingSeconds] = React.useState(null);

  React.useEffect(() => {
    if (!wateringStatus || !wateringStatus.startTime || !wateringStatus.duration) {
      setRemainingSeconds(null);
      return undefined;
    }
    const startTs = parseBackendTimestamp(wateringStatus.startTime)?.getTime();
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

  const ageDays = React.useMemo(() => {
    if (!plant?.planted_at) return null;
    const plantedDate = new Date(plant.planted_at);
    if (Number.isNaN(plantedDate.getTime())) {
      return null;
    }
    const diff = Date.now() - plantedDate.getTime();
    return Math.max(0, Math.floor(diff / MS_IN_DAY));
  }, [plant?.planted_at]);

  // Translitem: esli stadiya zadana v plant.growth_stage — berem ee; inache fallback na avto po vozrastu.
  const stageId = plant?.growth_stage && String(plant.growth_stage).trim()
    ? String(plant.growth_stage).trim()
    : getStageFromPlantAgeDays(ageDays);
  // Translitem: tip rastenija berem iz plant.plant_type; esli pustoj — ispol'zuem defolt "flowering" (est assets).
  const plantType = plant?.plant_type && String(plant.plant_type).trim()
    ? String(plant.plant_type).trim()
    : 'flowering';
  const showWateringBadge = wateringStatus && remainingSeconds !== null && remainingSeconds > 0;

  const handleOpenWatering = () => {
    if (primaryDevice?.device_id && onOpenWatering) {
      onOpenWatering({ deviceId: primaryDevice.device_id, plantId: plant.id });
    }
  };

  return (
    <Surface variant="card" padding="md" className="dashboard-plant-card">
      <div className="dashboard-plant-card__top">
        <div>
          <Title level={3} className="dashboard-plant-card__name">{plant.name}</Title>
          <Text tone="muted" className="dashboard-plant-card__group">
            {plant.plant_group ? plant.plant_group.name : 'Без группы'}
          </Text>
        </div>
        <Text tone="muted" className="dashboard-plant-card__age">
          {formatAge(plant.planted_at)}
        </Text>
      </div>

      {primaryDevice ? (
        <div className="dashboard-plant-card__body">
          <div className="dashboard-plant-card__avatar" aria-hidden="true">
            <PlantAvatar
              plantType={plantType}
              stage={stageId}
              variant="card"
              size="md"
            />
          </div>
          <div className="dashboard-plant-card__metrics">
            <SensorPill
              kind="air_temperature"
              value={primaryDevice.air_temperature}
              onClick={primaryDevice.device_id ? () => onOpenStats?.({ deviceId: primaryDevice.device_id, metric: 'air_temperature' }) : undefined}
              disabled={!primaryDevice.device_id}
            />
            <SensorPill
              kind="air_humidity"
              value={primaryDevice.air_humidity}
              onClick={primaryDevice.device_id ? () => onOpenStats?.({ deviceId: primaryDevice.device_id, metric: 'air_humidity' }) : undefined}
              disabled={!primaryDevice.device_id}
            />
            <SensorPill
              kind="soil_moisture"
              value={primaryDevice.soil_moisture}
              onClick={primaryDevice.device_id ? () => onOpenStats?.({ deviceId: primaryDevice.device_id, metric: 'soil_moisture' }) : undefined}
              disabled={!primaryDevice.device_id}
            />
            <SensorPill
              kind="watering"
              value={primaryDevice.is_watering}
              onClick={primaryDevice.device_id ? () => onOpenStats?.({ deviceId: primaryDevice.device_id, metric: 'watering' }) : undefined}
              highlight={Boolean(primaryDevice.is_watering)}
              disabled={!primaryDevice.device_id}
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
    </Surface>
  );
}

export default DashboardPlantCard;
