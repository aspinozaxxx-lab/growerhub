import React from 'react';
import './PlantAvatar.css';
import { resolveAvatarAsset } from './assets';

// Karta dlya chelovecheskih podpisey stadii
const STAGE_LABELS = {
  seed: 'Seed',
  seedling: 'Seedling',
  vegetative: 'Vegetative',
  preflower: 'Pre-flower',
  flowering: 'Flowering',
  ripening: 'Ripening',
  harvest_ready: 'Harvest ready',
};

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
  const stageLabel = STAGE_LABELS[stage] || stage || 'Unknown stage';
  const title = `${plantType || 'Plant'} ${stageLabel}`;

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

