import React from 'react';
import { Link } from 'react-router-dom';
import PlantAvatar from '../plant-avatar/PlantAvatar';
import { parseBackendTimestamp } from '../../utils/formatters';
import { DEFAULT_PLANT_TYPE_ID, getAutoStageFromAge, normalizePlantTypeId } from '../../domain/plants';
import SensorPill from '../ui/sensor-pill/SensorPill';
import Button from '../ui/Button';
import Surface from '../ui/Surface';
import { Title, Text } from '../ui/Typography';
import './DashboardPlantCard.css';

const MS_IN_DAY = 24 * 60 * 60 * 1000;

const SENSOR_KIND_MAP = {
  SOIL_MOISTURE: 'soil_moisture',
  AIR_TEMPERATURE: 'air_temperature',
  AIR_HUMIDITY: 'air_humidity',
};

const METRIC_LABELS = {
  air_temperature: 'Температура воздуха',
  air_humidity: 'Влажность воздуха',
  soil_moisture: 'Влажность почвы',
  watering: 'Поливы',
};

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
  const [remainingSeconds, setRemainingSeconds] = React.useState(null);
  const sensors = Array.isArray(plant?.sensors) ? plant.sensors : [];
  const pumps = Array.isArray(plant?.pumps) ? plant.pumps : [];
  const primaryPump = pumps[0];

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

  const plantTypeId = normalizePlantTypeId(plant?.plant_type || DEFAULT_PLANT_TYPE_ID);
  const stageId = plant?.growth_stage && String(plant.growth_stage).trim()
    ? String(plant.growth_stage).trim()
    : getAutoStageFromAge(plantTypeId, ageDays);
  const showWateringBadge = wateringStatus && remainingSeconds !== null && remainingSeconds > 0;

  const sensorsByKind = React.useMemo(() => {
    const map = {};
    sensors.forEach((sensor) => {
      const kind = SENSOR_KIND_MAP[sensor.type];
      if (!kind) return;
      if (!map[kind]) {
        map[kind] = sensor;
        return;
      }
      const currentTs = map[kind]?.last_ts ? new Date(map[kind].last_ts).getTime() : 0;
      const candidateTs = sensor.last_ts ? new Date(sensor.last_ts).getTime() : 0;
      if (candidateTs > currentTs) {
        map[kind] = sensor;
      }
    });
    return map;
  }, [sensors]);

  const handleOpenMetric = (metric) => {
    if (!plant?.id || !metric) return;
    onOpenStats?.({
      mode: 'plant',
      plantId: plant.id,
      metric,
      title: METRIC_LABELS[metric] || metric,
      subtitle: plant.name,
    });
  };

  const handleOpenWatering = () => {
    if (primaryPump?.id && onOpenWatering) {
      const label = primaryPump.label || `Насос ${primaryPump.channel ?? ''}`;
      onOpenWatering({ pumpId: primaryPump.id, pumpLabel: label, plantId: plant.id });
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

      {sensors.length > 0 ? (
        <div className="dashboard-plant-card__body">
          <div className="dashboard-plant-card__avatar" aria-hidden="true">
            <PlantAvatar
              plantType={plantTypeId}
              stage={stageId}
              variant="card"
              size="md"
            />
          </div>
          <div className="dashboard-plant-card__metrics">
            <SensorPill
              kind="air_temperature"
              value={sensorsByKind.air_temperature?.last_value}
              onClick={() => handleOpenMetric('air_temperature')}
              disabled={!plant?.id}
            />
            <SensorPill
              kind="air_humidity"
              value={sensorsByKind.air_humidity?.last_value}
              onClick={() => handleOpenMetric('air_humidity')}
              disabled={!plant?.id}
            />
            <SensorPill
              kind="soil_moisture"
              value={sensorsByKind.soil_moisture?.last_value}
              onClick={() => handleOpenMetric('soil_moisture')}
              disabled={!plant?.id}
            />
            <SensorPill
              kind="watering"
              value={Boolean(wateringStatus)}
              onClick={() => handleOpenMetric('watering')}
              highlight={Boolean(wateringStatus)}
              disabled={!plant?.id}
            />
            {showWateringBadge && (
              <div className="dashboard-plant-card__watering-badge">
                Идёт полив · осталось {formatRemaining(remainingSeconds)}
              </div>
            )}
          </div>
        </div>
      ) : (
        <Text tone="muted" className="dashboard-plant-card__empty">Нет подключённых датчиков</Text>
      )}

      <div className="dashboard-plant-card__footer">
        <div className="dashboard-plant-card__actions">
          <Button
            type="button"
            variant="secondary"
            onClick={handleOpenWatering}
            disabled={!primaryPump}
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
            Перейти >
          </Link>
        </div>
      </div>
    </Surface>
  );
}

export default DashboardPlantCard;

