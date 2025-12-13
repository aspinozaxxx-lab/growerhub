import { DEFAULT_PLANT_TYPE_ID, normalizePlantTypeId } from './types';
import { getStageLabel } from './stages';
import { PLANT_TYPE_CONFIG } from './config';

// Translitem: vite glob import vsekh png avatarov dlya plants domain.
const AVATAR_PNG_BY_PATH = import.meta.glob('./avatars/**/*.png', { eager: true, import: 'default' });

function getStagesForType(typeId) {
  const normalized = normalizePlantTypeId(typeId);
  const cfg = PLANT_TYPE_CONFIG[normalized] || PLANT_TYPE_CONFIG[DEFAULT_PLANT_TYPE_ID];
  return Array.isArray(cfg?.stages) ? cfg.stages : [];
}

function getAutoStageFromAge(typeId, ageDays) {
  const normalized = normalizePlantTypeId(typeId);
  const cfg = PLANT_TYPE_CONFIG[normalized] || PLANT_TYPE_CONFIG[DEFAULT_PLANT_TYPE_ID];
  const fallbackStage = cfg?.fallback_stage || 'seedling';
  const thresholds = Array.isArray(cfg?.auto_thresholds) ? cfg.auto_thresholds : [];

  const value = Number(ageDays);
  if (!Number.isFinite(value) || value < 0) {
    return fallbackStage;
  }

  let current = fallbackStage;
  for (const t of thresholds) {
    const min = Number(t?.min_age_days);
    if (!Number.isFinite(min)) continue;
    if (value >= min) {
      current = t.stage || current;
    }
  }

  const allowed = new Set(getStagesForType(normalized));
  return allowed.has(current) ? current : fallbackStage;
}

function getStageOptionsForType(lang = 'ru', typeId) {
  const stages = getStagesForType(typeId);
  const autoLabel = lang === 'ru' ? 'Авто по возрасту' : 'Auto by age';
  return [
    { value: '', label: autoLabel },
    ...stages.map((value) => ({ value, label: getStageLabel(value, lang) })),
  ];
}

function resolveAvatarAsset(typeId, stageId) {
  const normalizedType = normalizePlantTypeId(typeId);
  const stageKey = stageId ? String(stageId).trim() : '';

  const candidates = [];
  if (normalizedType && stageKey) {
    candidates.push(`./avatars/${normalizedType}/${stageKey}.png`);
  }
  if (normalizedType) {
    candidates.push(`./avatars/${normalizedType}/default.png`);
  }
  if (stageKey) {
    candidates.push(`./avatars/generic/${stageKey}.png`);
  }
  candidates.push('./avatars/generic/default.png');

  for (const key of candidates) {
    const hit = AVATAR_PNG_BY_PATH[key];
    if (hit) return hit;
  }
  return null;
}

export {
  getStagesForType,
  getAutoStageFromAge,
  getStageOptionsForType,
  resolveAvatarAsset,
};

