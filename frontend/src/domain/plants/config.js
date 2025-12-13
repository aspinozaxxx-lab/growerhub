// Translitem: konfiguraciya tipov rasteniy: dostupnye stadii + porogi dlya auto-stadii po vozrastu.

const PLANT_TYPE_CONFIG = {
  flowering_plants: {
    stages: ['seed', 'seedling', 'vegetative', 'preflower', 'flowering', 'ripening', 'harvest_ready'],
    fallback_stage: 'seedling',
    // Translitem: porogi iz istoricheskoj shkal'y (sohranyaem sovmestimost' s prezhnej avto-logikoj).
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 4, stage: 'seedling' },
      { min_age_days: 15, stage: 'vegetative' },
      { min_age_days: 36, stage: 'preflower' },
      { min_age_days: 51, stage: 'flowering' },
      { min_age_days: 76, stage: 'ripening' },
      { min_age_days: 91, stage: 'harvest_ready' },
    ],
  },
  houseplant: {
    stages: ['seed', 'seedling', 'vegetative', 'mature'],
    fallback_stage: 'seedling',
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 7, stage: 'seedling' },
      { min_age_days: 21, stage: 'vegetative' },
      { min_age_days: 60, stage: 'mature' },
    ],
  },
  leafy_greens: {
    stages: ['seed', 'seedling', 'vegetative', 'harvest_ready'],
    fallback_stage: 'seedling',
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 4, stage: 'seedling' },
      { min_age_days: 10, stage: 'vegetative' },
      { min_age_days: 25, stage: 'harvest_ready' },
    ],
  },
  fruiting_veg: {
    stages: ['seed', 'seedling', 'vegetative', 'flowering', 'fruit_set', 'ripening', 'harvest_ready'],
    fallback_stage: 'seedling',
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 4, stage: 'seedling' },
      { min_age_days: 14, stage: 'vegetative' },
      { min_age_days: 40, stage: 'flowering' },
      { min_age_days: 55, stage: 'fruit_set' },
      { min_age_days: 80, stage: 'ripening' },
      { min_age_days: 100, stage: 'harvest_ready' },
    ],
  },
};

export { PLANT_TYPE_CONFIG };

