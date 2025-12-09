import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

// Sloj gorshka: baza dlya gorshka v stile "sochnogo rasteniya"
function PotLayer({ stageConfig, environment, width, height }) {
  void stageConfig;
  void environment;

  const potHeight = height * 0.28;
  const rimHeight = potHeight * 0.22;
  const bodyHeight = potHeight - rimHeight * 0.6;
  const bodyTopY = height - potHeight + rimHeight * 0.6;
  const bodyBottomY = height - potHeight + bodyHeight;
  const topWidth = width * 0.78;
  const bottomWidth = width * 0.62;
  const topX = (width - topWidth) / 2;
  const bottomX = (width - bottomWidth) / 2;

  const bodyPath = `
    M ${topX + 4} ${bodyTopY}
    L ${topX + topWidth - 4} ${bodyTopY}
    Q ${topX + topWidth} ${bodyTopY + 2} ${topX + topWidth - 4} ${bodyTopY + 10}
    L ${bottomX + bottomWidth - 4} ${bodyBottomY - 6}
    Q ${bottomX + bottomWidth} ${bodyBottomY - 2} ${bottomX + bottomWidth - 4} ${bodyBottomY}
    L ${bottomX + 4} ${bodyBottomY}
    Q ${bottomX} ${bodyBottomY - 2} ${bottomX + 4} ${bodyBottomY - 6}
    L ${topX + 4} ${bodyTopY + 10}
    Q ${topX} ${bodyTopY + 2} ${topX + 4} ${bodyTopY}
    Z
  `;

  const shadowHeight = bodyHeight * 0.35;
  const shadowPath = `
    M ${bottomX + 6} ${bodyBottomY - shadowHeight}
    L ${bottomX + bottomWidth - 6} ${bodyBottomY - shadowHeight + 4}
    Q ${bottomX + bottomWidth} ${bodyBottomY - shadowHeight + 10} ${bottomX + bottomWidth - 6} ${bodyBottomY}
    L ${bottomX + 6} ${bodyBottomY}
    Q ${bottomX} ${bodyBottomY - 6} ${bottomX + 6} ${bodyBottomY - shadowHeight}
    Z
  `;

  const rimWidth = width * 0.82;
  const rimX = (width - rimWidth) / 2;
  const rimY = height - potHeight - rimHeight * 0.2;
  const rimPath = `
    M ${rimX} ${rimY}
    L ${rimX + rimWidth} ${rimY}
    Q ${rimX + rimWidth} ${rimY + rimHeight * 0.6} ${rimX + rimWidth - 6} ${rimY + rimHeight}
    L ${rimX + 6} ${rimY + rimHeight}
    Q ${rimX} ${rimY + rimHeight * 0.6} ${rimX} ${rimY}
    Z
  `;

  return (
    <g className="plant-avatar__layer pot-layer">
      <path d={bodyPath} fill={PLANT_AVATAR_PALETTE.POT_BASE} />
      <path d={shadowPath} fill={PLANT_AVATAR_PALETTE.POT_SHADOW} opacity={0.85} />
      <path d={rimPath} fill={PLANT_AVATAR_PALETTE.POT_HIGHLIGHT} />
    </g>
  );
}

export default PotLayer;
