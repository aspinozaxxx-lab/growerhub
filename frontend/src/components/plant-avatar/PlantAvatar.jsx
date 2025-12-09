import React from 'react';
import plantPot from '../../assets/plant-pot.svg';
import { getFloweringStageConfig } from './packs/floweringPack';
import './PlantAvatar.css';

// Bazovyj komponent avatara rastenij bez slozhnoj logiki stadij
function PlantAvatar({
  // Identifikator i nazvanie nuzhny dlya svyazki s dannymi
  plantId,
  plantName,
  // stage — id stadii rosta (seed, seedling, ...), podderzhivaetsya dlya flowering paka
  stage,
  // Rezerv pod budushchee ispol'zovanie (vozrast, tip, sensery)
  plantedAt,
  plantType,
  environment,
  // Vneshnie varianty i razmery obolochki
  variant = 'card',
  size = 'md',
  // fillContainer — zapolnit' shirinu obshchego kontejnenera
  fillContainer = true,
}) {
  // Props rezerv pod budushie sloi (sroki posadki, tip, okruzhenie)
  void plantedAt;
  void plantType;
  void environment;

  // Vybor stadii: sejchas podderzhivaem tolko plantType === flowering
  const stageId = plantType === 'flowering' ? stage || 'vegetative' : null;
  const stageConfig = stageId ? getFloweringStageConfig(stageId) : null;

  // Klassovaya setka dlya variantov, razmerov i zapolneniya shiriny
  const className = [
    'plant-avatar',
    `plant-avatar--variant-${variant}`,
    `plant-avatar--size-${size}`,
    fillContainer ? 'plant-avatar--fill' : '',
    stageConfig?.id ? `plant-avatar--stage-${stageConfig.id}` : '',
  ]
    .filter(Boolean)
    .join(' ');

  const title = plantName || (plantId ? `Plant ${plantId}` : 'Plant avatar');

  return (
    <div
      className={className}
      aria-label={title}
      data-stage-id={stageConfig?.id || undefined}
      data-stage-label={stageConfig?.label || undefined}
    >
      {/* plant-avatar__frame — ramka s aspect ratio 3:4 i fonovymi gradientami */}
      <div className="plant-avatar__frame">
        <img
          src={plantPot}
          alt={plantName || 'Plant avatar'}
          className="plant-avatar__image"
        />
      </div>
      {stageConfig?.label && (
        <div className="plant-avatar__stage">
          {stageConfig.label}
        </div>
      )}
    </div>
  );
}

export default PlantAvatar;
