// Translitem: edinaya tochka pravdy dlya identifikatorov stadii i ih russkih podpisey.

// Translitem: angl. stage-id ostayutsya kak identifikatory v daneh/BD/API.
const PLANT_STAGE_IDS = [
  'seed',
  'seedling',
  'vegetative',
  'preflower',
  'flowering',
  'ripening',
  'harvest_ready',
];

// Translitem: russkie leybly dlya UI.
const STAGE_LABELS_RU = {
  seed: 'Семя',
  seedling: 'Рассада',
  vegetative: 'Вегетация',
  preflower: 'Предцвет',
  flowering: 'Цветение',
  ripening: 'Созревание',
  harvest_ready: 'Готово к сбору',
};

// Translitem: vozvrashchaet russkiy leybl dlya stadii; esli id neizvesten - vozvrashaet ishodnuyu stroku.
function getStageLabelRu(stageId) {
  if (!stageId) return '';
  const key = String(stageId);
  return STAGE_LABELS_RU[key] || key;
}

// Translitem: opcii dlya select (pustoe znachenie = "avto po vozrastu").
const STAGE_OPTIONS_RU = [
  { value: '', label: 'Авто по возрасту' },
  ...PLANT_STAGE_IDS.map((value) => ({ value, label: getStageLabelRu(value) })),
];

export { PLANT_STAGE_IDS, STAGE_OPTIONS_RU, getStageLabelRu };
