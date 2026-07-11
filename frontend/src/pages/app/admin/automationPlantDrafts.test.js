import { describe, expect, it } from 'vitest';
import {
  buildPlantItemsPayload,
  createPlantDrafts,
  findPlantDraft,
  togglePlantDraft,
  updatePlantDraftRate,
} from './automationPlantDrafts';

describe('automation plant drafts', () => {
  it('sohranyaet nullable skorost iz topology boksa', () => {
    expect(createPlantDrafts([
      { id: 1, rate_ml_per_hour: null },
      { id: 2, rate_ml_per_hour: 1500 },
    ])).toEqual([
      { plant_id: 1, rate_ml_per_hour: '' },
      { plant_id: 2, rate_ml_per_hour: '1500' },
    ]);
  });

  it('dobavlyaet i ubiraet rastenie bez skrytogo default rate', () => {
    const added = togglePlantDraft([], 7);
    expect(findPlantDraft(added, 7)).toEqual({ plant_id: 7, rate_ml_per_hour: '' });
    expect(togglePlantDraft(added, 7)).toEqual([]);
  });

  it('sobiraet items payload s nullable rate', () => {
    const drafts = updatePlantDraftRate(createPlantDrafts([{ id: 1 }]), 1, '2200');
    expect(buildPlantItemsPayload([...drafts, { plant_id: 2, rate_ml_per_hour: '' }])).toEqual([
      { plant_id: 1, rate_ml_per_hour: 2200 },
      { plant_id: 2, rate_ml_per_hour: null },
    ]);
  });

  it('otklonyaet nulevuyu skorost', () => {
    expect(() => buildPlantItemsPayload([{ plant_id: 1, rate_ml_per_hour: '0' }])).toThrow(
      'Скорость полива должна быть целым числом больше нуля',
    );
    expect(() => buildPlantItemsPayload([{ plant_id: 1, rate_ml_per_hour: '1.5' }])).toThrow(
      'Скорость полива должна быть целым числом больше нуля',
    );
  });
});
