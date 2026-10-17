// @vitest-environment node
import { beforeEach, expect, it, vi } from 'vitest';

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
