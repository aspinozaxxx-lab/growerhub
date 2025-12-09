import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

const LEAF_PATH_D = 'M 0 0 C 6 -6 14 -6 16 0 C 14 6 6 10 0 0 Z';

// Sloj list'ev: uproshchennaya sistema list'ev, zavisyashchaya ot leafDensity
function LeavesLayer({ stageConfig, width, height, layout }) {
  const leafDensity = stageConfig?.appearance?.leafDensity ?? 0;
  const leafSize = stageConfig?.appearance?.leafSize ?? 0.4;
  void width;
  void height;

  if (leafDensity <= 0) {
    return null;
  }

  const slots = layout?.leaves?.slots ?? [];
  if (slots.length === 0) {
    return null;
  }

  const sizeScale = Math.max(0.55, Math.min(1.3, leafSize * 1.3));

  const leaves = slots.map((slot, index) => {
    const flip = slot.side === 'left' ? -1 : 1;
    return (
      <path
        key={`leaf-${index}`}
        d={LEAF_PATH_D}
        fill={PLANT_AVATAR_PALETTE.LEAF_LIGHT}
        stroke={PLANT_AVATAR_PALETTE.LEAF_DARK}
        strokeWidth={1.4}
        transform={`translate(${slot.x}, ${slot.y}) scale(${flip * sizeScale * slot.scale}, ${sizeScale * slot.scale}) rotate(${slot.side === 'left' ? -18 : 18})`}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    );
  });

  return <g className="plant-avatar__layer leaves-layer">{leaves}</g>;
}

export default LeavesLayer;
