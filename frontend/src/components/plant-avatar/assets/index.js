import floweringSeed from './flowering/seed.svg';
import floweringSeedling from './flowering/seedling.svg';
import floweringVegetative from './flowering/vegetative.svg';
import floweringPreflower from './flowering/preflower.svg';
import floweringFlowering from './flowering/flowering.svg';
import floweringRipening from './flowering/ripening.svg';
import floweringHarvestReady from './flowering/harvest_ready.svg';

// Karta dostupnyh svg asetov po tipu i stadii
export const PLANT_AVATAR_ASSETS = {
  flowering: {
    seed: floweringSeed,
    seedling: floweringSeedling,
    vegetative: floweringVegetative,
    preflower: floweringPreflower,
    flowering: floweringFlowering,
    ripening: floweringRipening,
    harvest_ready: floweringHarvestReady,
  },
};

// Funkciya rezerva: probuem nayti asset, esli net - otdaem vegetative ili pervyj dostupnyj
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

