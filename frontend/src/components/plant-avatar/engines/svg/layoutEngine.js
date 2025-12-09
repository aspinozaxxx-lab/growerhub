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

  const potHeight = height * 0.34;
  const rimHeight = potHeight * 0.18;
  const rimThickness = rimHeight * 0.45;
  const bodyHeight = potHeight - rimHeight;
  const topWidth = width * 0.64;
  const bottomWidth = topWidth * 0.78;
  const insetX = (width - topWidth) / 2;

  const potBottomY = height - height * 0.02;
  const potTopY = potBottomY - potHeight + rimHeight / 2;

  const soilWidth = topWidth * 0.88;
  const soilCenterX = width / 2;
  const soilRx = soilWidth / 2;
  const soilRy = rimHeight * 0.45;
  const soilCenterY = potTopY + rimHeight * 0.1;
  const soilTopY = soilCenterY - soilRy;
  const soilBottomY = soilCenterY + soilRy;

  const plantTopMargin = height * 0.1;
  const plantBottomY = soilCenterY;
  const plantTopY = plantTopMargin;
  const plantHeight = Math.max(plantBottomY - plantTopY, 10);

  const stemHeight = Math.max(12, plantHeight * heightFactor);
  const stemThickness = 4 + stemThicknessFactor * 6;
  const stemBaseX = width / 2;
  const stemBaseY = plantBottomY;

  const leafSlots = [];
  const minLeaves = 3;
  const midLeaves = 5;
  const maxLeaves = 7;
  let leafCount = minLeaves;
  if (leafDensity >= 0.66) {
    leafCount = maxLeaves;
  } else if (leafDensity >= 0.33) {
    leafCount = midLeaves;
  }

  const leafSpan = stemHeight * 0.82;
  const leafStartOffset = stemHeight * 0.18;
  const leafStep = leafCount > 1 ? leafSpan / (leafCount - 1) : 0;
  for (let i = 0; i < leafCount; i += 1) {
    const side = i % 2 === 0 ? 'left' : 'right';
    const y = stemBaseY - leafStartOffset - i * leafStep;
    const progress = leafCount > 1 ? i / (leafCount - 1) : 0;
    const xOffset = stemThickness * 1.5 + progress * stemThickness * 0.6;
    const scale = 0.85 + (1 - Math.abs(0.5 - progress) * 1.4) * 0.15;
    leafSlots.push({
      x: stemBaseX + (side === 'left' ? -xOffset : xOffset),
      y,
      scale,
      side,
      angle: side === 'left' ? -26 + progress * -6 : 26 + progress * 6,
    });
  }

  const flowers = [];
  if (flowerDensity > 0 && flowerSize > 0) {
    let flowerCount = 1;
    if (flowerDensity >= 0.66) {
      flowerCount = 3;
    } else if (flowerDensity >= 0.33) {
      flowerCount = 2;
    }

    const stemTopY = stemBaseY - stemHeight;
    const spread = stemThickness * 1.1;
    for (let i = 0; i < flowerCount; i += 1) {
      const offset = i - (flowerCount - 1) / 2;
      const x = stemBaseX + offset * spread;
      const y = stemTopY - 3 - Math.abs(offset) * 1.5;
      const scale = 0.75 + flowerSize * 0.6;
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
      rimThickness,
      bodyHeight,
      topWidth,
      bottomWidth,
      insetX,
    },
    soil: {
      topY: soilTopY,
      centerX: soilCenterX,
      centerY: soilCenterY,
      bottomY: soilBottomY,
      rx: soilRx,
      ry: soilRy,
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
