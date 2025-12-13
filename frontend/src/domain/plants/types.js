// Translitem: spisok dopustimyh tipov rasteniy dlya UI/BD/API (klyuch - typeId).
// Translitem: typeId hraniotsya kak stroka v plant.plant_type; labels - tol'ko dlya UI.

const DEFAULT_PLANT_TYPE_ID = 'flowering_plants';

const PLANT_TYPES = [
  { id: 'flowering_plants', labels: { ru: 'Цветущие растения', en: 'Flowering plants' } },
  { id: 'houseplant', labels: { ru: 'Комнатные декоративные', en: 'Houseplants' } },
  { id: 'leafy_greens', labels: { ru: 'Зелень и салаты', en: 'Leafy greens' } },
  { id: 'fruiting_veg', labels: { ru: 'Плодоносящие', en: 'Fruiting vegetables' } },
];

// Translitem: bystraya karta dlya poiska.
const PLANT_TYPES_BY_ID = Object.fromEntries(PLANT_TYPES.map((t) => [t.id, t]));

// Translitem: podderzhka legacy typeId iz starogo UI (do pereimenenovaniya).
const PLANT_TYPE_ALIASES = {
  flowering: 'flowering_plants',
};

function normalizePlantTypeId(typeId) {
  if (!typeId) return DEFAULT_PLANT_TYPE_ID;
  const raw = String(typeId).trim();
  if (!raw) return DEFAULT_PLANT_TYPE_ID;
  const mapped = PLANT_TYPE_ALIASES[raw] || raw;
  return PLANT_TYPES_BY_ID[mapped] ? mapped : DEFAULT_PLANT_TYPE_ID;
}

function getPlantTypeLabel(typeId, lang = 'ru') {
  const normalized = normalizePlantTypeId(typeId);
  const meta = PLANT_TYPES_BY_ID[normalized];
  if (!meta) return normalized;
  return meta.labels?.[lang] || meta.labels?.ru || meta.labels?.en || normalized;
}

function getPlantTypeOptions(lang = 'ru') {
  return PLANT_TYPES.map((t) => ({ value: t.id, label: t.labels?.[lang] || t.labels?.ru || t.labels?.en || t.id }));
}

export {
  DEFAULT_PLANT_TYPE_ID,
  PLANT_TYPES,
  getPlantTypeLabel,
  getPlantTypeOptions,
  normalizePlantTypeId,
};

