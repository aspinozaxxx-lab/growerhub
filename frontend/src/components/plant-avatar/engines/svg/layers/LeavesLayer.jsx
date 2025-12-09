import React from 'react';

// Sloj list'ev: neskol'ko ellipsov po bokam steblya, zavisit ot leafDensity/leafSize
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

  const leafCount = Math.max(0, Math.round(leafDensity * 8));
  const leaves = [];
  for (let i = 0; i < leafCount; i += 1) {
    const progress = (i + 1) / (leafCount + 1);
    const y = soilTop - stemHeight * progress;
    const dir = i % 2 === 0 ? -1 : 1;
    const offset = (8 + i) * 0.4;
    const rx = Math.max(3, leafSize * 8);
    const ry = Math.max(2, leafSize * 6);
    leaves.push(
      <ellipse
        key={`leaf-${i}`}
        cx={centerX + dir * offset}
        cy={y}
        rx={rx}
        ry={ry}
        fill="#74b36a"
        transform={`rotate(${dir * 12}, ${centerX + dir * offset}, ${y})`}
      />,
    );
  }

  if (leafCount === 0) {
    return null;
  }

  return (
    <g className="plant-avatar__layer leaves-layer">
      {leaves}
    </g>
  );
}

export default LeavesLayer;
