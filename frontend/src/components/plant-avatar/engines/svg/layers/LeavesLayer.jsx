import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

const LEAF_PATH_D = 'M 0 0 C 6 -6 14 -6 16 0 C 14 6 6 10 0 0 Z';

// Sloj list'ev: uproshchennaya sistema list'ev, zavisyashchaya ot leafDensity
function LeavesLayer({ stageConfig, width, height }) {
  const leafDensity = stageConfig?.appearance?.leafDensity ?? 0;
  const leafSize = stageConfig?.appearance?.leafSize ?? 0.4;
  const heightFactor = stageConfig?.layout?.heightFactor ?? 0.4;

  const potHeight = height * 0.28;
  const soilHeight = potHeight * 0.22;
  const soilTop = height - potHeight - soilHeight;
  const maxStemHeight = soilTop - 6;
  const stemHeight = Math.max(8, maxStemHeight * Math.min(Math.max(heightFactor, 0), 1));
  const centerX = width / 2;

  if (leafDensity <= 0) {
    return null;
  }

  const sizeScale = Math.max(0.55, Math.min(1.3, leafSize * 1.3));
  let leafCount = 3;
  if (leafDensity >= 0.3 && leafDensity < 0.7) {
    leafCount = 5;
  } else if (leafDensity >= 0.7) {
    leafCount = 7;
  }

  const layout = [
    { x: -12, y: -stemHeight * 0.12, angle: -26, scale: 1 },
    { x: 12, y: -stemHeight * 0.18, angle: 24, scale: 1 },
    { x: -15, y: -stemHeight * 0.32, angle: -18, scale: 0.96 },
    { x: 15, y: -stemHeight * 0.42, angle: 20, scale: 0.98 },
    { x: -10, y: -stemHeight * 0.52, angle: -16, scale: 0.9 },
    { x: 11, y: -stemHeight * 0.62, angle: 18, scale: 0.92 },
    { x: -7, y: -stemHeight * 0.72, angle: -12, scale: 0.88 },
  ];

  const leaves = layout.slice(0, leafCount).map((leaf, index) => (
    <path
      key={`leaf-${index}`}
      d={LEAF_PATH_D}
      fill={PLANT_AVATAR_PALETTE.LEAF_LIGHT}
      stroke={PLANT_AVATAR_PALETTE.LEAF_DARK}
      strokeWidth={1.4}
      transform={`translate(${centerX + leaf.x}, ${soilTop + leaf.y}) rotate(${leaf.angle}) scale(${sizeScale * leaf.scale})`}
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  ));

  return <g className="plant-avatar__layer leaves-layer">{leaves}</g>;
}

export default LeavesLayer;
