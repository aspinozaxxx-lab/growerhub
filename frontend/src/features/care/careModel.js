import { zonedDateTimeInputToUtc } from '../../utils/formatters';
import { translateApp as t } from '../../locales/i18n';

export const CARE_ACTIONS = [
  ['watering', 'Полито вручную', '💧'], ['fertilizing', 'Подкормка', '🌿'],
  ['repotting', 'Пересадка', '🪴'], ['pruning', 'Обрезка', '✂️'],
  ['treatment', 'Обработка', '🩺'], ['inspection', 'Осмотр', '🔎'],
  ['photo', 'Фото', '📷'], ['note', 'Заметка', '✍️'],
  ['harvest', 'Сбор урожая', '🌾'],
];
export const careAction = (action) => action === 'feeding' ? ['feeding', 'Уход', '🌿'] : CARE_ACTIONS.find(([key]) => key === action) || CARE_ACTIONS[7];
export function careUtc(value) {
  const date = zonedDateTimeInputToUtc(value);
  if (!date) throw new Error(t('Укажите дату и время'));
  return date.toISOString().slice(0, 19);
}
export async function prepareCarePhoto(file) {
  if (!file?.type.startsWith('image/') || file.size > 20 * 1024 * 1024) throw new Error(t('Выберите фотографию до 20 МБ'));
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' }).catch(() => { throw new Error(t('Не удалось открыть фото. Выберите JPEG или PNG.')); });
  try {
    if (bitmap.width * bitmap.height > 48000000) throw new Error(t('Фотография слишком большая'));
    const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas'); canvas.width = Math.round(bitmap.width * scale); canvas.height = Math.round(bitmap.height * scale);
    const ctx = canvas.getContext('2d'); ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height); ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return await new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error(t('Не удалось подготовить фото'))), 'image/jpeg', 0.88));
  } finally { bitmap.close(); }
}
