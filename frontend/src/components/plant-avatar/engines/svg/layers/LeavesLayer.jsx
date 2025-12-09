import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

const LEAF_PATH_D = 'M 0 0 C 5 -9 16 -10 22 -2 C 17 8 7 10 0 0 Z';

// Sloj list'ev: akkuratnye kapelki, rasstavlennye vokrug steblya
function LeavesLayer({ stageConfig, width, height, layout }) {
  const leafDensity = stageConfig?.appearance?.leafDensity ?? 0;
  const leafSize = stageConfig?.appearance?.leafSize ?? 0.5;
  void width;
  void height;

  if (leafDensity <= 0) {
    return null;
  }

  const slots = layout?.leaves?.slots ?? [];
  if (slots.length === 0) {
    return null;
  }

  const sizeScale = Math.max(0.6, Math.min(1.25, leafSize * 1.2));

  const leaves = slots.map((slot, index) => {
    const flip = slot.side === 'left' ? -1 : 1;
    const scale = sizeScale * slot.scale;
    const rotation = slot.angle ?? (slot.side === 'left' ? -26 : 26);
    return (
      <g key={`leaf-${index}`} transform={`translate(${slot.x}, ${slot.y}) rotate(${rotation}) scale(${flip * scale}, ${scale})`}>
        <path
          d={LEAF_PATH_D}
          fill={PLANT_AVATAR_PALETTE.LEAF_LIGHT}
          stroke={PLANT_AVATAR_PALETTE.LEAF_DARK}
          strokeWidth={1.2}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M 2 0 C 6 -3 11 -2 14 2"
          fill="none"
          stroke={PLANT_AVATAR_PALETTE.LEAF_DARK}
          strokeWidth={0.9}
          strokeLinecap="round"
          opacity={0.6}
        />
      </g>
    );
  });

  return <g className="plant-avatar__layer leaves-layer">{leaves}</g>;
}

export default LeavesLayer;
