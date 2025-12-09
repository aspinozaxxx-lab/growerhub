import React from 'react';

// Sloj steblya: odna kolonka, vysota ot heightFactor, tolshchina ot stemThickness
function StemLayer({ stageConfig, width, height }) {
  const heightFactor = stageConfig?.layout?.heightFactor ?? 0.4;
  const stemThickness = stageConfig?.appearance?.stemThickness ?? 0.25;

  const potHeight = height * 0.28;
  const soilHeight = potHeight * 0.22;
  const soilTop = height - potHeight - soilHeight;
  const maxStemHeight = soilTop - 6;
  const stemHeight = Math.max(8, maxStemHeight * Math.min(Math.max(heightFactor, 0), 1));
  const centerX = width / 2;
  const stemWidth = Math.max(2, stemThickness * 8);

  return (
    <g className="plant-avatar__layer stem-layer">
      <rect
        x={centerX - stemWidth / 2}
        y={soilTop - stemHeight}
        width={stemWidth}
        height={stemHeight}
        rx={stemWidth / 2}
        fill="#4f8b4a"
      />
    </g>
  );
}

export default StemLayer;
