import React from 'react';
import { Link } from 'react-router-dom';
import PlantAvatar from '../plant-avatar/PlantAvatar';
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

function formatNumber(value, digits) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return null;
  }
  return Number(value).toFixed(digits);
}

function buildWateringTooltip(advice) {
  if (!advice) {
    return '';
  }
  const volume = formatNumber(advice.recommended_water_volume_l, 2);
  const ph = formatNumber(advice.recommended_ph, 2);
  const fertilizers = advice.recommended_fertilizers_per_liter || 'нет';
  if (!volume) {
    return '';
  }
  const parts = [`Полить: ${volume} L`, `Удобрения: ${fertilizers}`];
  if (ph) {
    parts.push(`pH: ${ph}`);
  }
  return parts.join(', ');
}

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

function DashboardPlantCard({
  plant,
  onOpenStats,
  onOpenWatering,
  onOpenJournal,
}) {
  const sensors = Array.isArray(plant?.sensors) ? plant.sensors : [];
  const pumps = Array.isArray(plant?.pumps) ? plant.pumps : [];
  const primaryPump = pumps[0];

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
  const isWatering = pumps.some((pump) => pump && pump.is_running);
  const wateringAdvice = plant?.watering_advice || null;
  const wateringPrevious = plant?.watering_previous || null;
  const wateringTooltip = React.useMemo(() => buildWateringTooltip(wateringAdvice), [wateringAdvice]);

  const boundSensors = React.useMemo(() => {
    if (!plant?.id) {
      return sensors;
    }
    return sensors.filter((sensor) => {
      const boundPlants = Array.isArray(sensor.bound_plants) ? sensor.bound_plants : [];
      return boundPlants.some((bound) => bound.id === plant.id);
    });
  }, [plant?.id, sensors]);

  const sensorsByKind = React.useMemo(() => {
    const map = {};
    boundSensors.forEach((sensor) => {
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
  }, [boundSensors]);

  const sensorPills = React.useMemo(() => {
    const order = ['air_temperature', 'air_humidity', 'soil_moisture'];
    return order
      .map((kind) => {
        const sensor = sensorsByKind[kind];
        if (!sensor) return null;
        return { kind, sensor };
      })
      .filter(Boolean);
  }, [sensorsByKind]);

  const hasMetrics = sensorPills.length > 0 || pumps.length > 0;
  const bodyClassName = hasMetrics
    ? 'dashboard-plant-card__body'
    : 'dashboard-plant-card__body dashboard-plant-card__body--single';

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
      onOpenWatering({
        pumpId: primaryPump.id,
        pumpLabel: label,
        plantId: plant.id,
        wateringAdvice,
        wateringPrevious,
      });
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

      <div className={bodyClassName}>
        <div className="dashboard-plant-card__avatar" aria-hidden="true">
          <PlantAvatar
            plantType={plantTypeId}
            stage={stageId}
            variant="card"
            size="sm"
          />
        </div>
        {hasMetrics && (
          <div className="dashboard-plant-card__metrics">
            {sensorPills.length > 0 && (
              <div className="dashboard-plant-card__metrics-group">
                {sensorPills.map(({ kind, sensor }) => (
                  <SensorPill
                    key={`${kind}-${sensor.id}`}
                    kind={kind}
                    value={sensor.last_value}
                    onClick={() => handleOpenMetric(kind)}
                    disabled={!plant?.id}
                  />
                ))}
              </div>
            )}
            {pumps.length > 0 && (
              <div className="dashboard-plant-card__metrics-group">
                <SensorPill
                  kind="watering"
                  value={isWatering}
                  onClick={() => handleOpenMetric('watering')}
                  highlight={Boolean(isWatering)}
                  disabled={!plant?.id}
                />
                {wateringAdvice?.is_due && (
                  <div className="dashboard-plant-card__watering-advice" title={wateringTooltip}>
                    <span className="dashboard-plant-card__watering-advice-icon" aria-hidden="true">💧</span>
                    <span className="dashboard-plant-card__watering-advice-text">Пора полить</span>
                  </div>
                )}
                {isWatering && (
                  <div className="dashboard-plant-card__watering-badge">
                    Идёт полив
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>

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
