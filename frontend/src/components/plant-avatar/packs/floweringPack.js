import seed from './flowering/seed.json';
import seedling from './flowering/seedling.json';
import vegetative from './flowering/vegetative.json';
import preflower from './flowering/preflower.json';
import flowering from './flowering/flowering.json';
import ripening from './flowering/ripening.json';
import harvestReady from './flowering/harvest_ready.json';

// Loader paka dlya tipa flowering: vydayet konfiguraciyu stadii po ee id
const floweringStagesById = {
  seed,
  seedling,
  vegetative,
  preflower,
  flowering,
  ripening,
  harvest_ready: harvestReady,
};

function getFloweringStageConfig(stageId) {
  const normalized = stageId && floweringStagesById[stageId];
  if (normalized) {
    return normalized;
  }
  // Fallback na vegetative, chtoby byl predskazuemyj defolt esli stadiya ne izvestna
  return floweringStagesById.vegetative;
}

export { floweringStagesById, getFloweringStageConfig };
