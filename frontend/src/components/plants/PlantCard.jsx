import React, { useMemo } from 'react';
import PlantAvatar from '../plant-avatar/PlantAvatar';
import {
  DEFAULT_PLANT_TYPE_ID,
  getAutoStageFromAge,
  getPlantTypeLabel,
  getStageLabel,
  normalizePlantTypeId,
} from '../../domain/plants';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import SensorPill from '../ui/sensor-pill/SensorPill';
import Surface from '../ui/Surface';
import { Title, Text } from '../ui/Typography';
import './PlantCard.css';

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

// Translitem: PlantCard - kartochka rasteniya na stranice spiska rastenij; pokazivaet metadannye i privyazannye sensory/pumpy.
function PlantCard({ plant, onEdit, onOpenJournal }) {
  const { openSensorStats } = useSensorStatsContext();
  const plantedDate = plant?.planted_at ? new Date(plant.planted_at) : null;

  const ageDays = useMemo(() => {
    if (!plantedDate || Number.isNaN(plantedDate.getTime())) return null;
    const diff = Date.now() - plantedDate.getTime();
    return Math.max(0, Math.floor(diff / (1000 * 60 * 60 * 24)));
  }, [plantedDate]);

  const plantTypeId = normalizePlantTypeId(plant?.plant_type || DEFAULT_PLANT_TYPE_ID);
  const manualStageId = plant?.growth_stage ? String(plant.growth_stage).trim() : '';
  const stageId = manualStageId || (ageDays !== null ? getAutoStageFromAge(plantTypeId, ageDays) : undefined);
  const stageLabel = stageId ? getStageLabel(stageId, 'ru') : '';

  const groupName = plant?.plant_group?.name || 'Без группы';
  const plantedLabel = plantedDate && !Number.isNaN(plantedDate.getTime())
    ? plantedDate.toLocaleDateString('ru-RU')
    : 'Дата не указана';

  const sensors = Array.isArray(plant?.sensors) ? plant.sensors : [];
  const pumps = Array.isArray(plant?.pumps) ? plant.pumps : [];

  const handleEdit = () => onEdit?.(plant);
  const handleOpenJournal = () => onOpenJournal?.(plant);

  const handleOpenMetric = (metric) => {
    if (!plant?.id || !metric) return;
    openSensorStats({
      mode: 'plant',
      plantId: plant.id,
      metric,
      title: METRIC_LABELS[metric] || metric,
      subtitle: plant.name,
    });
  };

  return (
    <Surface variant="card" padding="md" className="plant-card">
      <div className="plant-card__header">
        <div className="plant-card__title">
          <Title level={3} className="plant-card__name">{plant.name}</Title>
          <div className="plant-card__meta">
            {plant.plant_type && <span className="plant-card__tag">{getPlantTypeLabel(plant.plant_type, 'ru')}</span>}
            {plant.strain && <span className="plant-card__tag">{plant.strain}</span>}
          </div>
          <Text tone="muted" className="plant-card__group">{groupName}</Text>
        </div>
        <div className="plant-card__actions">
          <button type="button" className="plant-card__edit" onClick={handleEdit} aria-label="Редактировать">
            ?
          </button>
        </div>
      </div>

      <div className="plant-card__body">
        <div className="plant-card__avatar" aria-hidden="true">
          <PlantAvatar plantType={plantTypeId} stage={stageId} variant="card" size="md" />
        </div>
        <div className="plant-card__info">
          <div className="plant-card__row">
            <Text as="span" tone="muted" className="plant-card__label">Дата посадки</Text>
            <span className="plant-card__value">{plantedLabel}</span>
          </div>
          <div className="plant-card__row">
            <Text as="span" tone="muted" className="plant-card__label">Возраст / Стадия</Text>
            <span className="plant-card__value">
              {ageDays !== null ? `${ageDays} дн.` : '-'}
              {stageLabel ? ` · ${stageLabel}` : ''}
            </span>
          </div>
        </div>
      </div>

      <div className="plant-card__section">
        <div className="plant-card__section-title">Датчики</div>
        {sensors.length === 0 && <div className="plant-card__empty">Нет датчиков</div>}
        {sensors.length > 0 && (
          <div className="plant-card__metrics">
            {sensors.map((sensor) => {
              const kind = SENSOR_KIND_MAP[sensor.type] || 'soil_moisture';
              return (
                <SensorPill
                  key={sensor.id}
                  kind={kind}
                  value={sensor.last_value}
                  onClick={() => handleOpenMetric(kind)}
                />
              );
            })}
          </div>
        )}
      </div>

      <div className="plant-card__section">
        <div className="plant-card__section-title">Насосы</div>
        {pumps.length === 0 && <div className="plant-card__empty">Нет насосов</div>}
        {pumps.length > 0 && (
          <div className="plant-card__pumps">
            {pumps.map((pump) => {
              const bound = Array.isArray(pump.bound_plants)
                ? pump.bound_plants.find((bp) => bp.id === plant.id)
                : null;
              const rate = bound?.rate_ml_per_hour;
              return (
                <div key={pump.id} className="plant-card__pump">
                  <div className="plant-card__pump-title">Насос · канал {pump.channel ?? '-'}</div>
                  {bound && <div className="plant-card__pump-rate">rate: {rate ?? '-'} мл/ч</div>}
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="plant-card__footer">
        <button type="button" className="plant-card__journal" onClick={handleOpenJournal}>
          Журнал
        </button>
      </div>
    </Surface>
  );
}

export default PlantCard;
