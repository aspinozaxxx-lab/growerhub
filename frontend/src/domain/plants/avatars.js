import catalog from '../../../../backend/src/main/resources/plant/avatars.json';
import assets from './avatarAssets.generated.json';
import { normalizePlantTypeId } from './types';

export function getPlantAvatarOptions(locale = 'ru') {
  return catalog.map(item => ({ value: item.id, label: locale === 'en' ? item.nameEn : item.nameRu,
    stages: item.stages.map(stage => ({ value: stage.id, label: locale === 'en' ? stage.nameEn : stage.nameRu })),
  }));
}

export function resolvePlantAvatar({ avatarKey, avatarStage, plantType, stage } = {}) {
  const selected = catalog.find(item => item.id === avatarKey);
  const selectedStage = selected?.stages.find(item => item.id === avatarStage) || selected?.stages.at(-1);
  const type = normalizePlantTypeId(plantType);
  const candidates = [selected && `selected/${selected.id}/${selectedStage.id}`, stage && `${type}/${stage}`, `${type}/default`, stage && `generic/${stage}`, 'generic/default'];
  const images = candidates.map(key => assets[key]).find(Boolean);
  if (!images?.length) return null;
  return {
    ...(images.find(item => item.width === 192) || images[0]),
    srcSet: images.map(item => `${item.src} ${item.width}w`).join(', '),
    label: selected ? { ru: `${selected.nameRu} · ${selectedStage.nameRu}`, en: `${selected.nameEn} · ${selectedStage.nameEn}` } : null,
  };
}
