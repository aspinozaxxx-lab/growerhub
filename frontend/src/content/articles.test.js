// @vitest-environment node
import { beforeEach, expect, it, vi } from 'vitest';
import fs from 'node:fs';
import matter from 'gray-matter';

const { loadMarkdownIndex } = vi.hoisted(() => ({ loadMarkdownIndex: vi.fn() }));
const mockMarkdownIndex = () => {
  loadMarkdownIndex();
  const body = `---
title: Fixture
---
## Article body

[Another article](/articles/tsiklamen-doma-prohlada-i-poliv)

[About](/about/)
`;
  return { default: {
    ru: { '../../content/articles/fixture.md': async () => body },
    en: { '../../content/en/articles/fixture.md': async () => body },
  } };
};

beforeEach(() => {
  vi.resetModules();
  vi.clearAllMocks();
  vi.doMock('./articleMarkdown', mockMarkdownIndex);
});

it.each(['ru', 'en'])('katalog %s ne zagruzhaet kartu Markdown do zaprosa teksta', async (locale) => {
  const { getArticleById, getArticles, loadArticleBody } = await import('./articles');
  const article = getArticleById('tsiklamen-doma-prohlada-i-poliv', locale);
  expect(article).toBeDefined();
  expect(getArticles(locale)).toContain(article);
  expect(loadMarkdownIndex).not.toHaveBeenCalled();

  const html = await loadArticleBody({ ...article, source_file: 'fixture.md' });
  expect(loadMarkdownIndex).toHaveBeenCalledTimes(1);
  expect(html).toContain('<h2>Article body</h2>');
  expect(html).not.toContain('title: Fixture');
  expect(html).toContain(`href="${locale === 'en' ? '/en' : ''}/articles/${article.slug}/"`);
  expect(html).toContain(`href="${locale === 'en' ? '/en' : ''}/about/"`);

  await loadArticleBody({ ...article, source_file: 'fixture.md' });
  expect(loadMarkdownIndex).toHaveBeenCalledTimes(1);
});

it('otsutstvuyushchiy Markdown vozvrashchaet oshibku dlya sushchestvuyushchego fallback stranicy', async () => {
  const { loadArticleBody } = await import('./articles');
  await expect(loadArticleBody({ locale: 'en', source_file: 'missing.md' }))
    .rejects.toThrow('Markdown module is missing: en/missing.md');
});

it.each(['ru', 'en'])('kompaktnye metadata %s vosstanavlivayut defaults i sohranyayut yavnyj otkaz ot CTA', async locale => {
  const { getArticleById, getArticles } = await import('./articles');
  const article = getArticleById('kartinki-rasteniy-i-stadii-rosta', locale);
  expect(article.product_cta).toBe(true);
  expect(article.hero_in_body).toBe(true);
  expect(article.download).toBeNull();
  expect(article.source_file).toBe(`${article.slug}.md`);
  expect(getArticles(locale).some(item => item.product_cta === false)).toBe(true);
});

it.each(['ru', 'en'])('metadata vseh statey %s sovpadayut s ishodnym Markdown bez poter dannyh', async (locale) => {
  const { getArticles } = await import('./articles');
  for (const article of getArticles(locale)) {
    const directory = locale === 'en' ? '../../content/en/articles/' : '../../content/articles/';
    const raw = fs.readFileSync(new URL(`${directory}${article.source_file}`, import.meta.url), 'utf8');
    const { data, content } = matter(raw);
    const related = data.related || [];
    const expected = (Array.isArray(related) ? related : String(related).split(','))
      .map(String).map((id) => id.trim()).filter(Boolean);
    const date = value => value instanceof Date ? value.toISOString() : value;
    expect(article, article.id).toMatchObject({
      id: locale === 'ru' ? data.slug : data.translation_of,
      slug: data.slug,
      title: data.title,
      summary: data.summary,
      created_at: date(data.created_at),
      updated_at: date(data.updated_at || data.created_at),
      cluster: data.cluster || 'vyrashchivanie-i-uhod',
      related: expected,
      hero_image: data.hero_image || '',
      hero_alt: data.hero_alt || data.title,
      hero_in_body: Boolean(data.hero_image && content.includes(`](${data.hero_image})`)),
      download: data.download || null,
      product_cta: data.product_cta ?? true,
    });
  }
});
