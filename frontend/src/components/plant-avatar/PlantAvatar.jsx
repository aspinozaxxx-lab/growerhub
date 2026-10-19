import React from 'react';
import './PlantAvatar.css';
import {
  getPlantTypeLabel,
  getStageLabel,
  normalizePlantTypeId,
  resolvePlantAvatar,
} from '../../domain/plants';
import { getCurrentLocale, translateApp } from '../../locales/i18n';

// Translitem: obshchij avatar s gotovymi WebP razmerami; vybor ne menyaet vid i stadiyu.
function PlantAvatar({
  avatarKey,
  avatarStage,
  // plantType - typeId rastenija (naprimer, flowering_plants)
  plantType,
  // stage - stageId rosta (seed, seedling, vegetative, preflower, flowering, ripening, harvest_ready, ...)
  stage,
  // variant - stil obolochki (card, detail, mini)
  variant = 'card',
  // size - razmer obolochki (xs, sm, md, lg, xl)
  size = 'md',
  sizes,
  showStage = true,
}) {
  const typeId = plantType ? normalizePlantTypeId(plantType) : null;
  const asset = resolvePlantAvatar({ avatarKey, avatarStage, plantType: typeId, stage });
  const locale = getCurrentLocale();
  const stageLabel = stage ? getStageLabel(stage, locale) : '';
  const typeLabel = plantType ? getPlantTypeLabel(plantType, locale) : translateApp("Растение");
  const title = asset?.label?.[locale] || `${typeLabel}${stageLabel ? ` · ${stageLabel}` : ''}`;
  const displayWidth = { xs: 64, sm: 96, md: 96, lg: 260, xl: 320 }[size] || 96;

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
        {asset ? (
          <img className="plant-avatar__image" src={asset.src} srcSet={asset.srcSet}
            sizes={sizes || `${displayWidth}px`} width={asset.width} height={asset.height}
            alt={title} loading="lazy" decoding="async" />
        ) : (
          <div className="plant-avatar__placeholder">{stageLabel}</div>
        )}
      </div>
      {showStage && stageLabel && (
        <div className="plant-avatar__stage">
          {stageLabel}
        </div>
      )}
    </div>
  );
}

export default PlantAvatar;
