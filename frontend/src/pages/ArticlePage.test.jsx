import { act } from '@testing-library/react';
import { renderToString } from 'react-dom/server';
import { hydrateRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import ArticlePage from './ArticlePage';
import { getArticleBySlug } from '../content/articles';
import { getArticlePath, getPublicPath } from '../domain/localizedRoutes';
import { ORGANIZATION_ID, SITE_URL } from '../domain/siteConfig';
import { changeLocale, translatePublic } from '../locales/i18n';

vi.mock('../content/articleMetadata.generated.json', () => {
  const definitions = [
    ['legacy', undefined], ['enabled', true], ['standalone', false], ['download', false], ['related', true],
  ];
  const rows = (locale) => definitions.map(([id, flag]) => {
    const row = [id, `${id}-${locale}`, `Article ${id}`, `Summary ${id}`, '2026-10-08', '2026-10-08',
      'zhurnal-i-sovetnik-uhoda', ['related'], '', '', `${id}.md`, false,
      id === 'download' ? { url: '/material.pdf', label: 'Download material' } : null];
    if (flag !== undefined) row.push(flag);
    return row;
  });
  return { default: {
    fields: ['id', 'slug', 'title', 'summary', 'created_at', 'updated_at', 'cluster', 'related',
      'hero_image', 'hero_alt', 'source_file', 'hero_in_body', 'download', 'product_cta'],
    ru: rows('ru'), en: rows('en'),
  } };
});
vi.mock('../domain/siteConfig', async (importOriginal) => ({
  ...await importOriginal(), DEMO_PUBLIC_ENABLED: true,
}));
vi.mock('../components/PlatformStartLink', () => ({ default: () => null }));
vi.mock('../utils/analytics', () => ({ trackProductGoal: vi.fn(), trackTelegramContact: vi.fn() }));

let root;
let container;
afterEach(async () => {
  if (root) await act(async () => root.unmount());
  root = null;
  container?.remove();
  document.head.querySelectorAll('link[rel="canonical"], link[data-growerhub-hreflang], script[data-growerhub-jsonld]')
    .forEach((element) => element.remove());
  await changeLocale('ru', { remember: false });
});

const renderArticle = async (id, locale) => {
  await changeLocale(locale, { remember: false });
  const article = getArticleBySlug(`${id}-${locale}`, locale);
  const path = getArticlePath(article, locale);
  const bodyHtml = '<h2>Article body</h2><p>Useful growing steps.</p><a href="https://example.org/source">Source</a>';
  const view = (
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={locale === 'en' ? '/en/articles/:slug/' : '/articles/:slug/'}
          element={<ArticlePage initialArticle={{ id, locale, bodyHtml }} />} />
      </Routes>
    </MemoryRouter>
  );
  container = document.createElement('div');
  document.body.appendChild(container);
  container.innerHTML = renderToString(view);
  return { article, path, view };
};

const assertArticle = (locale, showProductCta) => {
  expect(container.querySelectorAll('.article-demo-invite')).toHaveLength(showProductCta ? 1 : 0);
  expect(container.querySelectorAll('.lead-cta')).toHaveLength(showProductCta ? 1 : 0);
  const editorial = container.querySelector('aside.info-block');
  expect(editorial.querySelector('strong')).toHaveTextContent(translatePublic('Редакция GrowerHub'));
  expect(editorial.querySelectorAll('p')).toHaveLength(showProductCta ? 1 : 0);
  expect(editorial.querySelectorAll('a')).toHaveLength(showProductCta ? 2 : 0);
  expect(container.querySelector('.article-body')).toHaveTextContent('Useful growing steps.');
  expect(container.querySelector('.article-body a')).toHaveAttribute('href', 'https://example.org/source');
  expect(container.querySelector('.related-articles a')).toHaveAttribute('href', `${locale === 'en' ? '/en' : ''}/articles/related-${locale}/`);
  expect(container.querySelector(`a[href="${getPublicPath('articles', locale)}"]`)).not.toBeNull();
};

it.each(['ru', 'en'].flatMap((locale) => ['legacy', 'enabled', 'standalone'].map((id) => ({ locale, id }))))(
  '$locale/$id sohranyaet kontrakt CTA i kontent mezhdu SSR i hydration',
  async ({ locale, id }) => {
    const { article, path, view } = await renderArticle(id, locale);
    const showProductCta = id !== 'standalone';
    expect(article.product_cta).toBe({ legacy: undefined, enabled: true, standalone: false }[id]);
    assertArticle(locale, showProductCta);
    const staticText = container.textContent;
    const recoverableError = vi.fn();
    await act(async () => { root = hydrateRoot(container, view, { onRecoverableError: recoverableError }); });
    expect(recoverableError).not.toHaveBeenCalled();
    expect(container.textContent).toBe(staticText);
    assertArticle(locale, showProductCta);
    expect(document.head.querySelector('link[rel="canonical"]')).toHaveAttribute('href', `${SITE_URL}${path}`);
    for (const language of ['ru', 'en']) {
      expect(document.head.querySelector(`link[hreflang="${language}"]`))
        .toHaveAttribute('href', `${SITE_URL}${language === 'en' ? '/en' : ''}/articles/${id}-${language}/`);
    }
    const schema = JSON.parse(document.head.querySelector('script[data-growerhub-jsonld="true"]').textContent);
    expect(schema).toMatchObject({ '@type': 'BlogPosting', inLanguage: locale, url: `${SITE_URL}${path}`,
      author: { '@id': ORGANIZATION_ID, name: 'GrowerHub' }, publisher: { '@id': ORGANIZATION_ID } });
  },
);

it.each(['ru', 'en'])('opt-out %s sohranyaet skachivanie materiala', async (locale) => {
  const { view } = await renderArticle('download', locale);
  assertArticle(locale, false);
  const staticText = container.textContent;
  await act(async () => { root = hydrateRoot(container, view); });
  expect(container.textContent).toBe(staticText);
  expect(container.querySelector('a[href="/material.pdf"]')).toHaveTextContent('Download material');
  assertArticle(locale, false);
});
