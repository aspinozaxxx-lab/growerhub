import { translateApp } from '../../locales/i18n';
// Translitem: edinaya karta stadiy rosta (klyuch - stageId), labels po yazykam.
// Translitem: stageId hraniotsya kak stroka v plant.growth_stage (pustaya stroka = auto po vozrastu).

const STAGES = {
  seed: { labels: { ru: translateApp("Семя"), en: 'Seed' } },
  seedling: { labels: { ru: translateApp("Рассада"), en: 'Seedling' } },
  vegetative: { labels: { ru: translateApp("Вегетация"), en: 'Vegetative' } },
  preflower: { labels: { ru: translateApp("Предцвет"), en: 'Preflower' } },
  flowering: { labels: { ru: translateApp("Цветение"), en: 'Flowering' } },
  bolting: { labels: { ru: translateApp("Стрелкование"), en: 'Bolting' } },
  fruit_set: { labels: { ru: translateApp("Завязь плодов"), en: 'Fruit set' } },
  ripening: { labels: { ru: translateApp("Созревание"), en: 'Ripening' } },
  mature: { labels: { ru: translateApp("Зрелое растение"), en: 'Mature' } },
  harvest_ready: { labels: { ru: translateApp("Готово к сбору"), en: 'Harvest ready' } },
};

function getStageLabel(stageId, lang = 'ru') {
  if (!stageId) return '';
  const key = String(stageId).trim();
  if (!key) return '';
  const meta = STAGES[key];
  if (!meta) return key;
  return meta.labels?.[lang] || meta.labels?.ru || meta.labels?.en || key;
}

export { STAGES, getStageLabel };
