import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

// Sloj gorshka: okruglyj cilindr s myagkim obodkom
function PotLayer({ stageConfig, environment, width, height, layout }) {
  void stageConfig;
  void environment;
  void width;
  void height;

  const centerX = (layout?.width ?? width) / 2;
  const topY = layout?.pot?.topY ?? height * 0.68;
  const bottomY = layout?.pot?.bottomY ?? height;
  const rimHeight = layout?.pot?.rimHeight ?? height * 0.06;
  const rimThickness = layout?.pot?.rimThickness ?? rimHeight * 0.45;
  const topWidth = layout?.pot?.topWidth ?? (width - (layout?.pot?.insetX ?? width * 0.18) * 2);
  const bottomWidth = layout?.pot?.bottomWidth ?? topWidth * 0.78;

  const rimRx = topWidth / 2;
  const rimRy = rimHeight / 2;
  const innerRimRx = rimRx - rimThickness;
  const innerRimRy = rimRy - rimThickness * 0.6;
  const bodyTopY = topY + rimRy * 0.65;
  const bodyBottomY = bottomY;
  const bodyTopHalf = bodyTopY + rimRy * 0.2;
  const bottomRx = bottomWidth / 2;

  const bodyPath = `
    M ${centerX - rimRx + 3} ${bodyTopY}
    L ${centerX + rimRx - 3} ${bodyTopY}
    Q ${centerX + rimRx + 3} ${bodyTopHalf} ${centerX + bottomRx} ${bodyBottomY - 2}
    Q ${centerX + bottomRx - 2} ${bodyBottomY + 2} ${centerX + bottomRx - 6} ${bodyBottomY}
    L ${centerX - bottomRx + 6} ${bodyBottomY}
    Q ${centerX - bottomRx + 2} ${bodyBottomY + 2} ${centerX - bottomRx} ${bodyBottomY - 2}
    Q ${centerX - rimRx - 3} ${bodyTopHalf} ${centerX - rimRx + 3} ${bodyTopY}
    Z
  `;

  const rimFrontPath = `
    M ${centerX - rimRx} ${topY}
    C ${centerX - rimRx * 0.65} ${topY + rimRy * 1.1} ${centerX + rimRx * 0.65} ${topY + rimRy * 1.1} ${centerX + rimRx} ${topY}
    L ${centerX + rimRx} ${topY + rimThickness}
    C ${centerX + rimRx * 0.68} ${topY + rimRy * 1.35} ${centerX - rimRx * 0.68} ${topY + rimRy * 1.35} ${centerX - rimRx} ${topY + rimThickness}
    Z
  `;

  const highlightPath = `
    M ${centerX - rimRx * 0.8} ${bodyTopY + rimRy * 0.2}
    C ${centerX - rimRx * 0.72} ${bodyTopY + rimRy * 1.2} ${centerX - rimRx * 0.6} ${bottomY - (bottomY - bodyTopY) * 0.45} ${centerX - rimRx * 0.6} ${bottomY - (bottomY - bodyTopY) * 0.18}
    C ${centerX - rimRx * 0.58} ${bottomY - (bottomY - bodyTopY) * 0.12} ${centerX - rimRx * 0.5} ${bottomY - (bottomY - bodyTopY) * 0.08} ${centerX - rimRx * 0.48} ${bottomY - (bottomY - bodyTopY) * 0.06}
    L ${centerX - rimRx * 0.36} ${bottomY - (bottomY - bodyTopY) * 0.18}
    C ${centerX - rimRx * 0.4} ${bottomY - (bottomY - bodyTopY) * 0.3} ${centerX - rimRx * 0.45} ${bodyTopY + rimRy * 0.7} ${centerX - rimRx * 0.52} ${bodyTopY + rimRy * 0.2}
    Z
  `;

  return (
    <g className="plant-avatar__layer pot-layer">
      <ellipse cx={centerX} cy={topY} rx={rimRx} ry={rimRy} fill={PLANT_AVATAR_PALETTE.POT_HIGHLIGHT} />
      <ellipse cx={centerX} cy={topY} rx={innerRimRx} ry={innerRimRy} fill={PLANT_AVATAR_PALETTE.POT_BASE} />
      <path d={bodyPath} fill={PLANT_AVATAR_PALETTE.POT_BASE} />
      <path d={rimFrontPath} fill={PLANT_AVATAR_PALETTE.POT_SHADOW} opacity={0.95} />
      <path d={highlightPath} fill={PLANT_AVATAR_PALETTE.POT_HIGHLIGHT} opacity={0.85} />
    </g>
  );
}

export default PotLayer;
