import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

// Sloj cvetov: neytral'noe decorativnoe socvetie, zavisyashchee ot flowerDensity/flowerSize
function FlowerLayer({ stageConfig, width, height }) {
  const flowerDensity = stageConfig?.appearance?.flowerDensity ?? 0;
  const flowerSize = stageConfig?.appearance?.flowerSize ?? 0;
  const heightFactor = stageConfig?.layout?.heightFactor ?? 0.4;

  if (flowerDensity <= 0 || flowerSize <= 0) {
    return null;
  }

  const potHeight = height * 0.28;
  const soilHeight = potHeight * 0.22;
  const soilTop = height - potHeight - soilHeight;
  const maxStemHeight = soilTop - 6;
  const stemHeight = Math.max(8, maxStemHeight * Math.min(Math.max(heightFactor, 0), 1));
  const stemTop = soilTop - stemHeight;
  const centerX = width / 2;

  let flowerCount = 1;
  let scaleBase = 0.8;
  if (flowerDensity >= 0.3 && flowerDensity < 0.7) {
    flowerCount = 3;
    scaleBase = 1;
  } else if (flowerDensity >= 0.7) {
    flowerCount = 4;
    scaleBase = 1.15;
  }

  const renderFlower = (offsetIndex) => {
    const spread = 8;
    const x = centerX + (offsetIndex - (flowerCount - 1) / 2) * spread;
    const y = stemTop + 4 - Math.abs(offsetIndex - (flowerCount - 1) / 2) * 2;
    const scale = Math.max(0.6, flowerSize * 1.1 * scaleBase);
    const petalRadius = 3.6 * scale;
    const centerRadius = 2 * scale;
    const petals = Array.from({ length: 6 }, (_, idx) => {
      const angle = (idx / 6) * Math.PI * 2;
      const px = x + Math.cos(angle) * petalRadius * 1.5;
      const py = y + Math.sin(angle) * petalRadius * 1.2;
      return <circle key={`petal-${idx}`} cx={px} cy={py} r={petalRadius} fill={PLANT_AVATAR_PALETTE.FLOWER_LIGHT} />;
    });

    return (
      <g key={`flower-${offsetIndex}`}>
        {petals}
        <circle cx={x} cy={y} r={centerRadius} fill={PLANT_AVATAR_PALETTE.FLOWER_DARK} />
      </g>
    );
  };

  return (
    <g className="plant-avatar__layer flower-layer">
      {Array.from({ length: flowerCount }, (_, idx) => renderFlower(idx))}
    </g>
  );
}

export default FlowerLayer;
