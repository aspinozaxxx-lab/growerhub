import React from 'react';
import PotLayer from './layers/PotLayer';
import SoilLayer from './layers/SoilLayer';
import StemLayer from './layers/StemLayer';
import LeavesLayer from './layers/LeavesLayer';
import FlowerLayer from './layers/FlowerLayer';
import { computePlantLayout } from './layoutEngine';

const VIEWBOX_WIDTH = 100; // Bazovaya shirina rendera 3:4
const VIEWBOX_HEIGHT = 133; // Bazovaya vysota rendera 3:4

/**
 * Bazovyj SVG-renderer dlya PlantAvatar: sobiraet sloi i derzhit aspect 3:4.
 * Priemaet stageConfig iz paka i tekuschee okruzhenie, vishnaivaet sloi cherez <g>.
 */
function SvgAvatarRenderer({ stageConfig, environment, variant, size, className }) {
  // variant/size seichas ne menyaem, no derzhim dlya buduschego stylinga
  void variant;
  void size;

  const layout = computePlantLayout(stageConfig, VIEWBOX_WIDTH, VIEWBOX_HEIGHT);

  return (
    <svg
      className={`plant-avatar__svg ${className || ''}`}
      viewBox={`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`}
      role="img"
      aria-hidden="true"
      preserveAspectRatio="xMidYMid meet"
    >
      <PotLayer stageConfig={stageConfig} environment={environment} width={VIEWBOX_WIDTH} height={VIEWBOX_HEIGHT} layout={layout} />
      <SoilLayer stageConfig={stageConfig} environment={environment} width={VIEWBOX_WIDTH} height={VIEWBOX_HEIGHT} layout={layout} />
      <StemLayer stageConfig={stageConfig} environment={environment} width={VIEWBOX_WIDTH} height={VIEWBOX_HEIGHT} layout={layout} />
      <LeavesLayer stageConfig={stageConfig} environment={environment} width={VIEWBOX_WIDTH} height={VIEWBOX_HEIGHT} layout={layout} />
      <FlowerLayer stageConfig={stageConfig} environment={environment} width={VIEWBOX_WIDTH} height={VIEWBOX_HEIGHT} layout={layout} />
    </svg>
  );
}

export default SvgAvatarRenderer;
