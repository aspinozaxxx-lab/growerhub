import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

// Sloj gorshka: baza dlya gorshka v stile "sochnogo rasteniya"
function PotLayer({ stageConfig, environment, width, height, layout }) {
  void stageConfig;
  void environment;
  void width;
  void height;

  const bodyTopY = layout?.pot?.topY ?? height * 0.7;
  const bodyBottomY = layout?.pot?.bottomY ?? height;
  const rimHeight = layout?.pot?.rimHeight ?? 8;
  const insetX = layout?.pot?.insetX ?? 8;
  const bodyHeight = layout?.pot?.bodyHeight ?? (height * 0.3 - rimHeight);
  const effectiveWidth = layout?.width ?? width;
  const topWidth = effectiveWidth - insetX * 2;
  const bottomWidth = topWidth * 0.8;
  const topX = (effectiveWidth - topWidth) / 2;
  const bottomX = (effectiveWidth - bottomWidth) / 2;

  const bodyPath = `
    M ${topX + 4} ${bodyTopY}
    L ${topX + topWidth - 4} ${bodyTopY}
    Q ${topX + topWidth} ${bodyTopY + 3} ${topX + topWidth - 5} ${bodyTopY + 12}
    L ${bottomX + bottomWidth - 4} ${bodyBottomY - 6}
    Q ${bottomX + bottomWidth} ${bodyBottomY - 2} ${bottomX + bottomWidth - 5} ${bodyBottomY}
    L ${bottomX + 5} ${bodyBottomY}
    Q ${bottomX} ${bodyBottomY - 2} ${bottomX + 5} ${bodyBottomY - 6}
    L ${topX + 4} ${bodyTopY + 12}
    Q ${topX} ${bodyTopY + 3} ${topX + 4} ${bodyTopY}
    Z
  `;

  const shadowHeight = bodyHeight * 0.32;
  const shadowPath = `
    M ${bottomX + 6} ${bodyBottomY - shadowHeight}
    L ${bottomX + bottomWidth - 6} ${bodyBottomY - shadowHeight + 5}
    Q ${bottomX + bottomWidth} ${bodyBottomY - shadowHeight + 10} ${bottomX + bottomWidth - 6} ${bodyBottomY}
    L ${bottomX + 6} ${bodyBottomY}
    Q ${bottomX} ${bodyBottomY - 6} ${bottomX + 6} ${bodyBottomY - shadowHeight}
    Z
  `;

  const rimWidth = topWidth * 1.04;
  const rimX = (effectiveWidth - rimWidth) / 2;
  const rimY = bodyTopY - rimHeight * 0.9;
  const rimPath = `
    M ${rimX} ${rimY}
    L ${rimX + rimWidth} ${rimY}
    Q ${rimX + rimWidth} ${rimY + rimHeight * 0.5} ${rimX + rimWidth - 6} ${rimY + rimHeight}
    L ${rimX + 6} ${rimY + rimHeight}
    Q ${rimX} ${rimY + rimHeight * 0.5} ${rimX} ${rimY}
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
