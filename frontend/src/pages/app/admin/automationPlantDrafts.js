function listOrEmpty(value) {
  return Array.isArray(value) ? value : [];
}

function normalizedPlantId(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : value;
}

export function createPlantDrafts(plants) {
  return listOrEmpty(plants).map((plant) => ({
    plant_id: normalizedPlantId(plant.plant_id ?? plant.id),
    rate_ml_per_hour: plant.rate_ml_per_hour === null || plant.rate_ml_per_hour === undefined
      ? ''
      : String(plant.rate_ml_per_hour),
  }));
}

export function findPlantDraft(items, plantId) {
  return listOrEmpty(items).find((item) => String(item.plant_id) === String(plantId)) || null;
}

export function togglePlantDraft(items, plantId) {
  const list = listOrEmpty(items);
  if (findPlantDraft(list, plantId)) {
    return list.filter((item) => String(item.plant_id) !== String(plantId));
  }
  return [...list, { plant_id: normalizedPlantId(plantId), rate_ml_per_hour: '' }];
}

export function updatePlantDraftRate(items, plantId, value) {
  return listOrEmpty(items).map((item) => (
    String(item.plant_id) === String(plantId)
      ? { ...item, rate_ml_per_hour: value }
      : item
  ));
}

export function buildPlantItemsPayload(items) {
  return listOrEmpty(items).map((item) => {
    const rawRate = item.rate_ml_per_hour;
    if (rawRate === '' || rawRate === null || rawRate === undefined) {
      return { plant_id: normalizedPlantId(item.plant_id), rate_ml_per_hour: null };
    }
    const rate = Number(rawRate);
    if (!Number.isInteger(rate) || rate <= 0) {
      throw new Error('Скорость полива должна быть целым числом больше нуля или оставаться пустой');
    }
    return { plant_id: normalizedPlantId(item.plant_id), rate_ml_per_hour: rate };
  });
}
