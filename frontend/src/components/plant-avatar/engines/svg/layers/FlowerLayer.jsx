import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

// Sloj cvetov: prostye myagkie butony nad list'jami
function FlowerLayer({ stageConfig, width, height, layout }) {
  const flowerDensity = stageConfig?.appearance?.flowerDensity ?? 0;
  const flowerSize = stageConfig?.appearance?.flowerSize ?? 0;
  void width;
  void height;

  if (flowerDensity <= 0 || flowerSize <= 0) {
    return null;
  }

  const slots = layout?.flowers?.slots ?? [];
  if (slots.length === 0) {
    return null;
  }

  const renderFlower = (slot, index) => {
    const scale = slot.scale;
    const petalRadius = 3.4 * scale;
    const centerRadius = 2.1 * scale;
    const petals = Array.from({ length: 5 }, (_, idx) => {
      const angle = (idx / 5) * Math.PI * 2;
      const px = slot.x + Math.cos(angle) * petalRadius * 1.35;
      const py = slot.y + Math.sin(angle) * petalRadius * 1.1;
      return <circle key={`petal-${index}-${idx}`} cx={px} cy={py} r={petalRadius} fill={PLANT_AVATAR_PALETTE.FLOWER_LIGHT} />;
    });

    return (
      <g key={`flower-${index}`}>
        {petals}
        <circle cx={slot.x} cy={slot.y} r={centerRadius} fill={PLANT_AVATAR_PALETTE.FLOWER_DARK} />
      </g>
    );
  };

  return <g className="plant-avatar__layer flower-layer">{slots.map(renderFlower)}</g>;
}

export default FlowerLayer;
