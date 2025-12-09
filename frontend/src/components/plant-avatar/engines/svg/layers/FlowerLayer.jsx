import React from 'react';

// Sloj cvetov: krugi v verhnej zone steblya, kolichestvo/razmer ot flowerDensity/flowerSize
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

  const flowerCount = Math.max(1, Math.round(flowerDensity * 6));
  const petals = [];
  for (let i = 0; i < flowerCount; i += 1) {
    const offset = (i - (flowerCount - 1) / 2) * 6;
    const radius = Math.max(2.5, flowerSize * 5);
    const y = stemTop + (i % 2 === 0 ? 2 : 6);
    petals.push(
      <g key={`flw-${i}`}>
        <circle cx={centerX + offset} cy={y} r={radius} fill="#f2b8d8" />
        <circle cx={centerX + offset} cy={y} r={radius * 0.45} fill="#f4e9b5" />
      </g>,
    );
  }

  return (
    <g className="plant-avatar__layer flower-layer">
      {petals}
    </g>
  );
}

export default FlowerLayer;
