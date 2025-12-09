// Funksiya dlya rascheta edinogo layouta PlantAvatar
// Vse koordinaty rasschityvayutsya vnutri edinogo viewBox, chtoby sloi byli vyrovneny

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

// Kommentarii na russkom translitom: eto layout-dvizhok, kotorый sobiraet geometriju
function computePlantLayout(stageConfig, width, height) {
  const heightFactor = clamp(stageConfig?.layout?.heightFactor ?? 0.5, 0, 1);
  const stemThicknessFactor = clamp(stageConfig?.appearance?.stemThickness ?? 0.25, 0, 1);
  const leafDensity = clamp(stageConfig?.appearance?.leafDensity ?? 0, 0, 1);
  const flowerDensity = clamp(stageConfig?.appearance?.flowerDensity ?? 0, 0, 1);
  const flowerSize = clamp(stageConfig?.appearance?.flowerSize ?? 0, 0, 1);

  const potHeight = height * 0.3;
  const rimHeight = potHeight * 0.18;
  const bodyHeight = potHeight - rimHeight;
  const insetX = width * 0.1;

  const potBottomY = height;
  const potTopY = potBottomY - bodyHeight;

  const soilHeight = potHeight * 0.18;
  const soilBottomY = potTopY - rimHeight * 0.1;
  const soilTopY = soilBottomY - soilHeight;

  const plantTopMargin = height * 0.1;
  const plantBottomY = soilTopY;
  const plantTopY = plantTopMargin;
  const plantHeight = Math.max(plantBottomY - plantTopY, 10);

  const stemHeight = Math.max(8, plantHeight * heightFactor);
  const stemThickness = 4 + stemThicknessFactor * 6;
  const stemBaseX = width / 2;
  const stemBaseY = plantBottomY;

  const leafSlots = [];
  const minLeaves = 2;
  const midLeaves = 5;
  const maxLeaves = 7;
  let leafCount = minLeaves;
  if (leafDensity >= 0.7) {
    leafCount = maxLeaves;
  } else if (leafDensity >= 0.3) {
    leafCount = midLeaves;
  }

  const leafSpan = stemHeight * 0.8;
  const leafStartY = stemBaseY - leafSpan * 0.2;
  const leafStep = leafSpan / Math.max(leafCount, 1);
  for (let i = 0; i < leafCount; i += 1) {
    const side = i % 2 === 0 ? 'left' : 'right';
    const y = leafStartY - i * leafStep;
    const xOffset = stemThickness * 1.8 + (i % 3) * 2;
    const scale = 0.9 + (i % 2) * 0.1;
    leafSlots.push({
      x: stemBaseX + (side === 'left' ? -xOffset : xOffset),
      y,
      scale,
      side,
    });
  }

  const flowers = [];
  if (flowerDensity > 0 && flowerSize > 0) {
    let flowerCount = 1;
    if (flowerDensity >= 0.7) {
      flowerCount = 4;
    } else if (flowerDensity >= 0.3) {
      flowerCount = 3;
    }

    const stemTopY = stemBaseY - stemHeight;
    const spread = stemThickness * 1.2;
    for (let i = 0; i < flowerCount; i += 1) {
      const offset = i - (flowerCount - 1) / 2;
      const x = stemBaseX + offset * spread;
      const y = stemTopY - 4 - Math.abs(offset) * 2;
      const scale = 0.7 + flowerSize * 0.8;
      flowers.push({ x, y, scale });
    }
  }

  return {
    width,
    height,
    pot: {
      bottomY: potBottomY,
      topY: potTopY,
      rimHeight,
      bodyHeight,
      insetX,
    },
    soil: {
      topY: soilTopY,
      bottomY: soilBottomY,
    },
    plantArea: {
      topY: plantTopY,
      bottomY: plantBottomY,
      height: plantHeight,
    },
    stem: {
      baseX: stemBaseX,
      baseY: stemBaseY,
      height: stemHeight,
      thickness: stemThickness,
    },
    leaves: {
      slots: leafSlots,
    },
    flowers: {
      slots: flowers,
    },
  };
}

export { computePlantLayout };
