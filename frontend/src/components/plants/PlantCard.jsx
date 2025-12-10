import React, { useMemo } from 'react';
import PlantAvatar from '../plant-avatar/PlantAvatar';
import { getStageFromPlantAgeDays } from '../plant-avatar/plantStageFromAge';
import DeviceCard from '../devices/DeviceCard';
import './PlantCard.css';

// Translitem: PlantCard - kartochka rasteniya na stranice spiska rastenij; pokazivaet osnovnye metadannye i svyazannye ustrojstva, imeet knopki Zhurnal i karandash.
function PlantCard({ plant, onEdit, onOpenJournal }) {
  const plantedDate = plant?.planted_at ? new Date(plant.planted_at) : null;

  const ageDays = useMemo(() => {
    if (!plantedDate || Number.isNaN(plantedDate.getTime())) return null;
    const diff = Date.now() - plantedDate.getTime();
    return Math.max(0, Math.floor(diff / (1000 * 60 * 60 * 24)));
  }, [plantedDate]);

  const stage = plant?.growth_stage || (ageDays !== null ? getStageFromPlantAgeDays(ageDays) : undefined);

  const groupName = plant?.plant_group?.name || '\u0411\u0435\u0437 \u0433\u0440\u0443\u043f\u043f\u044b';
  const plantedLabel = plantedDate && !Number.isNaN(plantedDate.getTime())
    ? plantedDate.toLocaleDateString('ru-RU')
    : '\u0414\u0430\u0442\u0430 \u043d\u0435 \u0443\u043a\u0430\u0437\u0430\u043d\u0430';

  const handleEdit = () => onEdit?.(plant);
  const handleOpenJournal = () => onOpenJournal?.(plant);

  return (
    <div className="plant-card">
      <div className="plant-card__header">
        <div className="plant-card__title">
          <div className="plant-card__name">{plant.name}</div>
          <div className="plant-card__meta">
            {plant.plant_type && <span className="plant-card__tag">{plant.plant_type}</span>}
            {plant.strain && <span className="plant-card__tag">{plant.strain}</span>}
          </div>
          <div className="plant-card__group">{groupName}</div>
        </div>
        <div className="plant-card__actions">
          <button type="button" className="plant-card__edit" onClick={handleEdit} aria-label="\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u0442\u044c">
            ??
          </button>
        </div>
      </div>

      <div className="plant-card__body">
        <div className="plant-card__avatar" aria-hidden="true">
          <PlantAvatar plantType={plant.plant_type || 'flowering'} stage={stage} variant="card" size="md" />
        </div>
        <div className="plant-card__info">
          <div className="plant-card__row">
            <span className="plant-card__label">\u0414\u0430\u0442\u0430 \u043f\u043e\u0441\u0430\u0434\u043a\u0438</span>
            <span className="plant-card__value">{plantedLabel}</span>
          </div>
          <div className="plant-card__row">
            <span className="plant-card__label">\u0412\u043e\u0437\u0440\u0430\u0441\u0442 / \u0421\u0442\u0430\u0434\u0438\u044f</span>
            <span className="plant-card__value">
              {ageDays !== null ? `${ageDays} \u0434\u043d.` : '\u2014'}
              {stage ? ` \u00b7 ${stage}` : ''}
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
          \u0416\u0443\u0440\u043d\u0430\u043b
        </button>
      </div>
    </div>
  );
}

export default PlantCard;
