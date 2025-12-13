// Translitem: edinaya karta stadiy rosta (klyuch - stageId), labels po yazykam.
// Translitem: stageId hraniotsya kak stroka v plant.growth_stage (pustaya stroka = auto po vozrastu).

const STAGES = {
  seed: { labels: { ru: 'Семя', en: 'Seed' } },
  seedling: { labels: { ru: 'Рассада', en: 'Seedling' } },
  vegetative: { labels: { ru: 'Вегетация', en: 'Vegetative' } },
  preflower: { labels: { ru: 'Предцвет', en: 'Preflower' } },
  flowering: { labels: { ru: 'Цветение', en: 'Flowering' } },
  bolting: { labels: { ru: 'Стрелкование', en: 'Bolting' } },
  fruit_set: { labels: { ru: 'Завязь плодов', en: 'Fruit set' } },
  ripening: { labels: { ru: 'Созревание', en: 'Ripening' } },
  mature: { labels: { ru: 'Зрелое растение', en: 'Mature' } },
  harvest_ready: { labels: { ru: 'Готово к сбору', en: 'Harvest ready' } },
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
