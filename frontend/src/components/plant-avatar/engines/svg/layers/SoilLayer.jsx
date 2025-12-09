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

function SoilLayer({ environment, width, height }) {
  const potHeight = height * 0.28;
  const soilHeight = potHeight * 0.22;
  const soilTop = height - potHeight - soilHeight * 0.2;
  const soilBase = soilTop + soilHeight;
  const soilWidth = width * 0.76;
  const soilX = (width - soilWidth) / 2;
  const curveRise = soilHeight * 0.35;

  const soilPath = `
    M ${soilX} ${soilBase}
    L ${soilX} ${soilTop + soilHeight * 0.4}
    Q ${soilX + soilWidth * 0.25} ${soilTop - curveRise} ${soilX + soilWidth / 2} ${soilTop}
    Q ${soilX + soilWidth * 0.75} ${soilTop - curveRise} ${soilX + soilWidth} ${soilTop + soilHeight * 0.4}
    L ${soilX + soilWidth} ${soilBase}
    Z
  `;

  const soilColor = pickSoilColor(environment?.soilMoisture);

  return (
    <g className="plant-avatar__layer soil-layer">
      <path d={soilPath} fill={soilColor} />
    </g>
  );
}

export default SoilLayer;
