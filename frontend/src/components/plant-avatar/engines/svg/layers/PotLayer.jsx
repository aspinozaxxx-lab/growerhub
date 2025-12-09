import React from 'react';

// Sloj gorshka: prostoj polygon v nizhnej chasti
function PotLayer({ width, height }) {
  const potHeight = height * 0.28;
  const topY = height - potHeight;
  const lipHeight = potHeight * 0.2;

  return (
    <g className="plant-avatar__layer pot-layer">
      <rect
        x={width * 0.1}
        y={topY}
        width={width * 0.8}
        height={potHeight}
        rx={6}
        fill="#d98555"
      />
      <rect
        x={width * 0.08}
        y={topY - lipHeight * 0.6}
        width={width * 0.84}
        height={lipHeight}
        rx={4}
        fill="#c97548"
      />
    </g>
  );
}

export default PotLayer;
