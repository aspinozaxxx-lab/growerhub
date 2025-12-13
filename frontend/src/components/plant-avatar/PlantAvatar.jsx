import React from 'react';
import './PlantAvatar.css';
import {
  getPlantTypeLabel,
  getStageLabel,
  normalizePlantTypeId,
  resolveAvatarAsset,
} from '../../domain/plants';

// Translitem: prostyj avatar rastenija: beret png-asset po tipu i stadii iz plants domain.
function PlantAvatar({
  // plantType - typeId rastenija (naprimer, flowering_plants)
  plantType,
  // stage - stageId rosta (seed, seedling, vegetative, preflower, flowering, ripening, harvest_ready, ...)
  stage,
  // variant - stil obolochki (card, detail, mini)
  variant = 'card',
  // size - razmer obolochki (xs, sm, md, lg, xl)
  size = 'md',
}) {
  const typeId = plantType ? normalizePlantTypeId(plantType) : null;
  const imageSrc = resolveAvatarAsset(typeId, stage);
  const stageLabel = stage ? getStageLabel(stage, 'ru') : '';
  const typeLabel = plantType ? getPlantTypeLabel(plantType, 'ru') : 'Растение';
  const title = `${typeLabel}${stageLabel ? ` · ${stageLabel}` : ''}`;

  const className = [
    'plant-avatar',
    `plant-avatar--variant-${variant}`,
    `plant-avatar--size-${size}`,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={className} aria-label={title}>
      {/* plant-avatar__frame - ramka s aspect ratio 3:4 dlya lyubogo kontenta */}
      <div className="plant-avatar__frame">
        {imageSrc ? (
          <img className="plant-avatar__image" src={imageSrc} alt={title} />
        ) : (
          <div className="plant-avatar__placeholder">{stageLabel}</div>
        )}
      </div>
      {stageLabel && (
        <div className="plant-avatar__stage">
          {stageLabel}
        </div>
      )}
    </div>
  );
}

export default PlantAvatar;
