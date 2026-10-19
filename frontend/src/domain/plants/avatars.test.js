import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import { expect, test } from 'vitest';
import { getPlantAvatarOptions, resolvePlantAvatar } from './avatars';

test('desyat rastenij imeyut chetyre podpisannye stadii i legkie prozrachnye WebP', async () => {
  const options = getPlantAvatarOptions('ru');
  expect(options).toHaveLength(10);
  expect(new Set(options.map(item => item.value)).size).toBe(10);
  for (const item of options) {
    expect(item.label).toMatch(/[А-Яа-я]/);
    expect(item.stages).toHaveLength(4);
    expect(new Set(item.stages.map(stage => stage.label)).size).toBe(4);
    for (const stage of item.stages) {
      expect(stage.label).toMatch(/[А-Яа-я]/);
      const asset = resolvePlantAvatar({ avatarKey: item.value, avatarStage: stage.value });
      expect(asset.src).toContain(`/selected/${item.value}/${stage.value}-192.webp`);
      expect(asset.label.ru).toContain(stage.label);
      const variants = asset.srcSet.split(', ').map(part => part.split(' '));
      expect(variants.map(([, width]) => width)).toEqual(['96w', '192w', '384w', '640w']);
      for (const [url, width] of variants) {
        const file = path.join(process.cwd(), 'public', url.slice(1));
        const buffer = await fs.readFile(file);
        const meta = await sharp(buffer).metadata();
        expect(meta.format).toBe('webp'); expect(meta.hasAlpha).toBe(true);
        expect(meta.width).toBe(Number.parseInt(width, 10)); expect(meta.height).toBe(meta.width);
        expect(buffer.length).toBeLessThan({ '96w': 12000, '192w': 35000, '384w': 80000, '640w': 150000 }[width]);
        if (width === '640w') expect((await sharp(buffer).stats()).isOpaque).toBe(false);
      }
    }
  }
}, 30000);

test('prezhnyaya kartinka po tipu i vozrastu tozhe ispolzuet umenshennye razmery', () => {
  const asset = resolvePlantAvatar({ plantType: 'flowering_plants', stage: 'harvest_ready' });
  expect(asset.src).toContain('/flowering_plants/harvest_ready-192.webp');
  expect(asset.srcSet).not.toContain('.png');
  expect(getPlantAvatarOptions('en').find(item => item.value === 'tomato').stages.at(-1).label).toBe('Harvest');
});
