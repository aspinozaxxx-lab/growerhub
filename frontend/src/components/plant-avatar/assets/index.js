// PNG versii assetov po stadiyam dlya avatara
import seedPng from './flowering/seed.png';
import seedlingPng from './flowering/seedling.png';
import vegetativePng from './flowering/vegetative.png';
import preflowerPng from './flowering/preflower.png';
import floweringPng from './flowering/flowering.png';
import ripeningPng from './flowering/ripening.png';
import harvestReadyPng from './flowering/harvest_ready.png';

// Karta dostupnyh PNG asetov po tipu i stadii
export const PLANT_AVATAR_ASSETS = {
  flowering: {
    seed: seedPng,
    seedling: seedlingPng,
    vegetative: vegetativePng,
    preflower: preflowerPng,
    flowering: floweringPng,
    ripening: ripeningPng,
    harvest_ready: harvestReadyPng,
  },
};

// Funkciya rezerva: probuem nayti png dlya tipa/stadii, esli net - otdaem vegetative ili pervyj dostupnyj
export function resolveAvatarAsset(plantType, stage) {
  const typeAssets = PLANT_AVATAR_ASSETS[plantType];
  if (!typeAssets) {
    return null;
  }

  const fromStage = stage ? typeAssets[stage] : null;
  if (fromStage) {
    return fromStage;
  }

  if (typeAssets.vegetative) {
    return typeAssets.vegetative;
  }

  const fallback = Object.values(typeAssets)[0];
  return fallback || null;
}

