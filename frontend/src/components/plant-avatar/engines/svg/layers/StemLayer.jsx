import React from 'react';
import { PLANT_AVATAR_PALETTE } from '../palette';

// Sloj steblya: zhivaya stoyka s legkim szhatiem kverhu
function StemLayer({ stageConfig, width, height, layout }) {
  void stageConfig;
  void width;
  void height;

  const stemHeight = layout?.stem?.height ?? 40;
  const centerX = layout?.stem?.baseX ?? (width / 2);
  const soilTop = layout?.stem?.baseY ?? height * 0.65;
  const bottomWidth = Math.max(3, layout?.stem?.thickness ?? 6);
  const topWidth = bottomWidth * 0.72;
  const stemTopY = soilTop - stemHeight;

  const leftBottom = centerX - bottomWidth / 2;
  const rightBottom = centerX + bottomWidth / 2;
  const leftTop = centerX - topWidth / 2;
  const rightTop = centerX + topWidth / 2;

  const stemPath = `
    M ${leftBottom} ${soilTop}
    L ${rightBottom} ${soilTop}
    Q ${rightBottom + 2} ${soilTop - 6} ${rightTop} ${stemTopY + 6}
    Q ${rightTop} ${stemTopY} ${rightTop - 2} ${stemTopY}
    L ${leftTop + 2} ${stemTopY}
    Q ${leftTop} ${stemTopY} ${leftTop} ${stemTopY + 6}
    Q ${leftBottom - 2} ${soilTop - 6} ${leftBottom} ${soilTop}
    Z
  `;

  const shadowWidth = Math.max(2, bottomWidth * 0.35);
  const shadowPath = `
    M ${rightTop - 1} ${stemTopY + 4}
    L ${rightBottom} ${soilTop}
    L ${rightBottom - shadowWidth} ${soilTop}
    L ${rightTop - shadowWidth * 0.6} ${stemTopY + 6}
    Z
  `;

  return (
    <g className="plant-avatar__layer stem-layer">
      <path d={stemPath} fill={PLANT_AVATAR_PALETTE.STEM_BASE} />
      <path d={shadowPath} fill={PLANT_AVATAR_PALETTE.STEM_SHADOW} opacity={0.95} />
    </g>
  );
}

export default StemLayer;
