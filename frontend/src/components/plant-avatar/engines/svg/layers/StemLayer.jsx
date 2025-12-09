import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

// Sloj steblya: odin myagkij izognutyj rostok iz tsentra pochvy
function StemLayer({ stageConfig, width, height, layout }) {
  void stageConfig;
  void width;
  void height;

  const stemHeight = layout?.stem?.height ?? 40;
  const centerX = layout?.stem?.baseX ?? (width / 2);
  const soilCenterY = layout?.stem?.baseY ?? height * 0.65;
  const stemThickness = Math.max(3, layout?.stem?.thickness ?? 6);
  const stemTopY = soilCenterY - stemHeight;

  const controlOffset = stemHeight * 0.28;
  const curveShift = stemThickness * 0.7;

  const stemSpine = `
    M ${centerX} ${soilCenterY}
    C ${centerX - curveShift} ${soilCenterY - controlOffset} ${centerX + curveShift} ${soilCenterY - controlOffset * 1.8} ${centerX} ${stemTopY}
  `;

  const shadowSpine = `
    M ${centerX + stemThickness * 0.18} ${soilCenterY}
    C ${centerX + curveShift * 0.6} ${soilCenterY - controlOffset} ${centerX + curveShift * 0.2} ${soilCenterY - controlOffset * 1.8} ${centerX + stemThickness * 0.18} ${stemTopY + stemThickness * 0.2}
  `;

  return (
    <g className="plant-avatar__layer stem-layer">
      <path
        d={stemSpine}
        fill="none"
        stroke={PLANT_AVATAR_PALETTE.STEM_BASE}
        strokeWidth={stemThickness}
        strokeLinecap="round"
      />
      <path
        d={shadowSpine}
        fill="none"
        stroke={PLANT_AVATAR_PALETTE.STEM_SHADOW}
        strokeWidth={Math.max(1, stemThickness * 0.4)}
        strokeLinecap="round"
        opacity={0.85}
      />
    </g>
  );
}

export default StemLayer;
