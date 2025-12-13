import React, { useMemo } from 'react';
import PlantAvatar from '../plant-avatar/PlantAvatar';
import {
  DEFAULT_PLANT_TYPE_ID,
  getAutoStageFromAge,
  getPlantTypeLabel,
  getStageLabel,
  normalizePlantTypeId,
} from '../../domain/plants';
import DeviceCard from '../devices/DeviceCard';
import Surface from '../ui/Surface';
import { Title, Text } from '../ui/Typography';
import './PlantCard.css';

// Translitem: PlantCard - kartochka rasteniya na stranice spiska rastenij; pokazivaet osnovnye metadannye i svyazannye ustrojstva, imeet knopki Zhurnal i karandash.
function PlantCard({ plant, onEdit, onOpenJournal }) {
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

  const handleEdit = () => onEdit?.(plant);
  const handleOpenJournal = () => onOpenJournal?.(plant);

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
            ✏
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

      {Array.isArray(plant.devices) && plant.devices.length > 0 && (
        <div className="plant-card__devices">
          {plant.devices.map((device) => (
            <DeviceCard key={device.id || device.device_id} device={device} variant="plant" />
          ))}
        </div>
      )}

      <div className="plant-card__footer">
        <button type="button" className="plant-card__journal" onClick={handleOpenJournal}>
          Журнал
        </button>
      </div>
    </Surface>
  );
}

export default PlantCard;
