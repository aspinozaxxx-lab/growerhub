import React from 'react';

// Sloj pochvy: prostaya poloska nad gorshkom s tsvetom ot vlagi
function SoilLayer({ environment, width, height }) {
  const potHeight = height * 0.28;
  const soilHeight = potHeight * 0.22;
  const topY = height - potHeight - soilHeight;

  const moisture = environment?.soilMoisture ?? null;
  const moisturePct = Number.isFinite(Number(moisture)) ? Math.min(Math.max(Number(moisture), 0), 100) : null;
  // Bol'she vlazhnost' — temnee pochva
  const drynessFactor = moisturePct === null ? 0.4 : 1 - moisturePct / 100;
  const baseColor = 180;
  const colorShift = Math.round(40 * drynessFactor);
  const soilColor = `rgb(${baseColor - colorShift}, ${120 - colorShift}, ${70 - colorShift})`;

  return (
    <g className="plant-avatar__layer soil-layer">
      <rect
        x={width * 0.1}
        y={topY}
        width={width * 0.8}
        height={soilHeight}
        rx={3}
        fill={soilColor}
      />
    </g>
  );
}

export default SoilLayer;
