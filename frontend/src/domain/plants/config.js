// Translitem: konfiguraciya tipov rasteniy: dostupnye stadii + porogi dlya auto-stadii po vozrastu.

const PLANT_TYPE_CONFIG = {
  flowering_plants: {
    stages: ['seed', 'seedling', 'vegetative', 'preflower', 'flowering', 'ripening', 'harvest_ready'],
    fallback_stage: 'seedling',
    // Translitem: bolee zhiznennye porogi po vozrastu (dni ot posadki) dlya auto-stadii.
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 7, stage: 'seedling' },
      { min_age_days: 21, stage: 'vegetative' },
      { min_age_days: 42, stage: 'preflower' },
      { min_age_days: 56, stage: 'flowering' },
      { min_age_days: 77, stage: 'ripening' },
      { min_age_days: 98, stage: 'harvest_ready' },
    ],
  },
  houseplant: {
    stages: ['seed', 'seedling', 'vegetative', 'mature'],
    fallback_stage: 'seedling',
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 14, stage: 'seedling' },
      { min_age_days: 60, stage: 'vegetative' },
      { min_age_days: 180, stage: 'mature' },
    ],
  },
  leafy_greens: {
    stages: ['seed', 'seedling', 'vegetative', 'harvest_ready'],
    fallback_stage: 'seedling',
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 4, stage: 'seedling' },
      { min_age_days: 10, stage: 'vegetative' },
      { min_age_days: 21, stage: 'harvest_ready' },
    ],
  },
  fruiting_veg: {
    stages: ['seed', 'seedling', 'vegetative', 'flowering', 'fruit_set', 'ripening', 'harvest_ready'],
    fallback_stage: 'seedling',
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 7, stage: 'seedling' },
      { min_age_days: 28, stage: 'vegetative' },
      { min_age_days: 50, stage: 'flowering' },
      { min_age_days: 65, stage: 'fruit_set' },
      { min_age_days: 85, stage: 'ripening' },
      { min_age_days: 100, stage: 'harvest_ready' },
    ],
  },
  succulents_cacti: {
    stages: ['seed', 'seedling', 'vegetative', 'mature', 'flowering'],
    fallback_stage: 'seedling',
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 21, stage: 'seedling' },
      { min_age_days: 120, stage: 'vegetative' },
      { min_age_days: 365, stage: 'mature' },
      { min_age_days: 730, stage: 'flowering' },
    ],
  },
  herbs_spices: {
    stages: ['seed', 'seedling', 'vegetative', 'harvest_ready', 'bolting'],
    fallback_stage: 'seedling',
    auto_thresholds: [
      { min_age_days: 0, stage: 'seed' },
      { min_age_days: 5, stage: 'seedling' },
      { min_age_days: 14, stage: 'vegetative' },
      { min_age_days: 28, stage: 'harvest_ready' },
      { min_age_days: 45, stage: 'bolting' },
    ],
  },
};

export { PLANT_TYPE_CONFIG };
