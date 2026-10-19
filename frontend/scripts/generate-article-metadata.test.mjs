// @vitest-environment node
import fs from 'node:fs';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

vi.mock('node:fs', () => ({
  default: {
    existsSync: vi.fn(),
    readdirSync: vi.fn(),
    readFileSync: vi.fn(),
    writeFileSync: vi.fn(),
  },
}));

let flags;
let clusters;
const news = JSON.stringify({
  labels: { fix: 'Update' },
  entries: [{ id: 'test-update', kind: 'fix', published_at: '2026-10-08', text: 'Update' }],
});
const markdown = (id, locale) => `---
slug: ${id}${locale === 'en' ? '-en' : ''}
${locale === 'en' ? `translation_of: ${id}\n` : ''}title: Article ${id}
summary: Summary ${id}
created_at: '2026-10-08'
${clusters[locale][id] ? `cluster: ${clusters[locale][id]}\n` : ''}
${flags[locale][id] === undefined ? '' : `product_cta: ${flags[locale][id]}\n`}---
Article body.
`;

beforeEach(() => {
  vi.resetModules();
  vi.clearAllMocks();
  flags = {
    ru: { legacy: undefined, enabled: 'true', standalone: 'false' },
    en: { legacy: undefined, enabled: 'true', standalone: 'false' },
  };
  clusters = { ru: {}, en: {} };
  fs.existsSync.mockReturnValue(true);
  fs.readdirSync.mockReturnValue(['legacy.md', 'enabled.md', 'standalone.md']);
  fs.readFileSync.mockImplementation((filePath) => {
    const normalized = filePath.replaceAll('\\', '/');
    if (normalized.endsWith('/news.json')) return news;
    const locale = normalized.includes('/content/en/') ? 'en' : 'ru';
    return markdown(normalized.split('/').at(-1).replace('.md', ''), locale);
  });
  vi.spyOn(console, 'log').mockImplementation(() => {});
});

it('statya bez rubriki popadaet v uhod vmesto ischeznoveniya iz kataloga', async () => {
  await import('./generate-article-metadata.mjs');
  const metadata = JSON.parse(fs.writeFileSync.mock.calls[0][1]);
  const index = metadata.fields.indexOf('cluster');
  for (const locale of ['ru', 'en']) {
    expect(metadata[locale].every((row) => row[index] === 'vyrashchivanie-i-uhod')).toBe(true);
  }
});

it('neizvestnaya rubrika ostanavlivaet publikaciyu', async () => {
  clusters.ru.standalone = 'missing-topic';
  await expect(import('./generate-article-metadata.mjs')).rejects.toThrow('Unknown article cluster');
  expect(fs.writeFileSync).not.toHaveBeenCalled();
});

it('rubriki originala i perevoda dolzhny sovpadat', async () => {
  clusters.en.standalone = 'svet-dlya-rasteniy';
  await expect(import('./generate-article-metadata.mjs')).rejects.toThrow('Article cluster differs between locales');
  expect(fs.writeFileSync).not.toHaveBeenCalled();
});

afterEach(() => vi.restoreAllMocks());

it('generator peredayot boolean opt-out i prezhniy default v obe lokali', async () => {
  await import('./generate-article-metadata.mjs');
  expect(fs.writeFileSync).toHaveBeenCalledTimes(1);
  const metadata = JSON.parse(fs.writeFileSync.mock.calls[0][1]);
  const idIndex = metadata.fields.indexOf('id');
  const flagIndex = metadata.fields.indexOf('product_cta');
  expect(flagIndex).toBeGreaterThan(-1);
  for (const locale of ['ru', 'en']) {
    expect(Object.fromEntries(metadata[locale].map((row) => [row[idIndex], row[flagIndex] ?? metadata.defaults.product_cta])))
      .toEqual({ legacy: true, enabled: true, standalone: false });
  }
});

it.each(['ru', 'en'].flatMap((locale) => ['"false"', 'null', '0'].map((value) => ({ locale, value }))))(
  'generator otklonyaet ne boolean $value v $locale do zapisi metadata',
  async ({ locale, value }) => {
    flags[locale].standalone = value;
    await expect(import('./generate-article-metadata.mjs'))
      .rejects.toThrow(`product_cta must be a boolean: ${locale}/standalone.md`);
    expect(fs.writeFileSync).not.toHaveBeenCalled();
  },
);
