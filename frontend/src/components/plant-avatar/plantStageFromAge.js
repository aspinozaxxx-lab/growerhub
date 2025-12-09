/**
 * Opredelyaet stadiyu rosta po kolichestvu dnej s posadki.
 * Skala: 0-3 seed, 4-14 seedling, 15-35 vegetative, 36-50 preflower,
 * 51-75 flowering, 76-90 ripening, 91+ harvest_ready.
 * @param {number|null|undefined} ageDays Kolichestvo dnej s posadki (mozhet byt undefined)
 * @returns {'seed'|'seedling'|'vegetative'|'preflower'|'flowering'|'ripening'|'harvest_ready'}
 */
function getStageFromPlantAgeDays(ageDays) {
  const value = Number(ageDays);
  if (!Number.isFinite(value) || value < 0) {
    // Zapasnoj variant esli net daty posadki
    return 'seedling';
  }
  if (value <= 3) return 'seed';
  if (value <= 14) return 'seedling';
  if (value <= 35) return 'vegetative';
  if (value <= 50) return 'preflower';
  if (value <= 75) return 'flowering';
  if (value <= 90) return 'ripening';
  return 'harvest_ready';
}

export { getStageFromPlantAgeDays };
