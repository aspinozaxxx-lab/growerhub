import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

// Sloj pochvy: myagkaya volna s tsvetom ot vlagi
const hexToRgb = (hex) => {
  const normalized = hex.replace('#', '');
  const value = parseInt(normalized, 16);
  return {
    r: (value >> 16) & 255,
    g: (value >> 8) & 255,
    b: value & 255,
  };
};

const mixColors = (from, to, ratio) => ({
  r: Math.round(from.r * (1 - ratio) + to.r * ratio),
  g: Math.round(from.g * (1 - ratio) + to.g * ratio),
  b: Math.round(from.b * (1 - ratio) + to.b * ratio),
});

// Funkciya vybora cveta pochvy po soilMoisture
const pickSoilColor = (moisture) => {
  const wet = hexToRgb(PLANT_AVATAR_PALETTE.SOIL_WET);
  const dry = hexToRgb(PLANT_AVATAR_PALETTE.SOIL_DRY);
  const hasValue = Number.isFinite(Number(moisture));
  if (!hasValue) {
    const mid = mixColors(wet, dry, 0.5);
    return `rgb(${mid.r}, ${mid.g}, ${mid.b})`;
  }

  const pct = Math.min(Math.max(Number(moisture), 0), 100);
  let drynessRatio = 0.5;
  if (pct < 30) {
    drynessRatio = 0.85;
  } else if (pct > 70) {
    drynessRatio = 0.15;
  } else {
    const span = 70 - 30;
    const progress = (pct - 30) / span;
    drynessRatio = 0.85 - progress * (0.85 - 0.15);
  }

  const mixed = mixColors(wet, dry, drynessRatio);
  return `rgb(${mixed.r}, ${mixed.g}, ${mixed.b})`;
};

function SoilLayer({ environment, width, height, layout }) {
  void width;
  void height;
  const soilCx = layout?.soil?.centerX ?? (layout?.soil?.rx ? (layout?.width ?? width) / 2 : (width / 2));
  const soilCy = layout?.soil?.centerY ?? layout?.soil?.topY ?? height * 0.62;
  const soilRx = layout?.soil?.rx ?? (layout?.width ?? width) * 0.35;
  const soilRy = layout?.soil?.ry ?? soilRx * 0.35;

  const soilColor = pickSoilColor(environment?.soilMoisture);

  const bumpPath = `
    M ${soilCx - soilRx * 0.45} ${soilCy + soilRy * 0.1}
    C ${soilCx - soilRx * 0.25} ${soilCy - soilRy * 0.4} ${soilCx + soilRx * 0.05} ${soilCy - soilRy * 0.35} ${soilCx + soilRx * 0.25} ${soilCy}
    C ${soilCx + soilRx * 0.05} ${soilCy + soilRy * 0.18} ${soilCx - soilRx * 0.15} ${soilCy + soilRy * 0.15} ${soilCx - soilRx * 0.45} ${soilCy + soilRy * 0.1}
    Z
  `;

  const shadowPath = `
    M ${soilCx - soilRx * 0.85} ${soilCy + soilRy * 0.2}
    C ${soilCx - soilRx * 0.55} ${soilCy + soilRy * 0.45} ${soilCx + soilRx * 0.55} ${soilCy + soilRy * 0.45} ${soilCx + soilRx * 0.85} ${soilCy + soilRy * 0.2}
    C ${soilCx + soilRx * 0.55} ${soilCy + soilRy * 0.55} ${soilCx - soilRx * 0.55} ${soilCy + soilRy * 0.55} ${soilCx - soilRx * 0.85} ${soilCy + soilRy * 0.2}
    Z
  `;

  return (
    <g className="plant-avatar__layer soil-layer">
      <ellipse cx={soilCx} cy={soilCy} rx={soilRx} ry={soilRy} fill={soilColor} />
      <path d={bumpPath} fill={PLANT_AVATAR_PALETTE.SOIL_WET} opacity={0.25} />
      <path d={shadowPath} fill={PLANT_AVATAR_PALETTE.SOIL_DRY} opacity={0.18} />
    </g>
  );
}

export default SoilLayer;
