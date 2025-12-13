import React from 'react';
import './PlantAvatar.css';
import { resolveAvatarAsset } from './assets';
import { getStageLabelRu } from '../../constants/plantStages';

// Prostyj avatar rastenija: beret staticheskij svg po tipu i stadii
function PlantAvatar({
  // plantType - tip rastenija (naprimer, flowering)
  plantType,
  // stage - stadiya rosta (seed, seedling, vegetative, preflower, flowering, ripening, harvest_ready)
  stage,
  // variant - stil obolochki (card, detail, mini)
  variant = 'card',
  // size - razmer obolochki (xs, sm, md, lg, xl)
  size = 'md',
}) {
  const imageSrc = resolveAvatarAsset(plantType, stage);
  // Translitem: podpisi stadii v UI pokazyvaem po-russki cherez edinyj modul.
  const stageLabel = stage ? getStageLabelRu(stage) : '';
  const title = `${plantType || 'Растение'}${stageLabel ? ` · ${stageLabel}` : ''}`;

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
