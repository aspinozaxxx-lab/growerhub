import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { renderToString } from 'react-dom/server';
import { hydrateRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ArticlesListPage from './ArticlesListPage';
import ArticleClusterPage from './ArticleClusterPage';
import ArticlePage from './ArticlePage';
import { getArticles } from '../content/articles';
import { getArticleClusters } from '../content/articleClusters';
import { getArticlePath, getClusterPath } from '../domain/localizedRoutes';
import { changeLocale } from '../locales/i18n';
import useArticleLibrary, { rememberArticle } from '../features/articles/useArticleLibrary';

vi.mock('../components/LeadCta', () => ({ default: () => null }));
vi.mock('../utils/analytics', () => ({ trackProductGoal: vi.fn() }));
const key = 'growerhub.article-library.v1';
const lightId = 'kak-izmerit-svet-dlya-rasteniy';
const light = getArticles('ru').find((article) => article.id === lightId);
const sync = (value) => {
  if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value);
  window.dispatchEvent(new StorageEvent('storage', { key }));
};
const app = (path = '/articles/') => <MemoryRouter initialEntries={[path]}><Routes>
  <Route path="/articles/" element={<ArticlesListPage />} />
  <Route path="/en/articles/" element={<ArticlesListPage />} />
  <Route path="/articles/clusters/:clusterSlug/" element={<ArticleClusterPage />} />
  <Route path="/articles/:slug/" element={<ArticlePage initialArticle={{ id: lightId, locale: 'ru', bodyHtml: '<p>Leaf light measurement.</p>' }} />} />
</Routes></MemoryRouter>;

beforeEach(async () => {
  await changeLocale('ru', { remember: false });
  // Podpiska poluchaet sbros dazhe pri povtornom monte stranicy.
  const Probe = () => { useArticleLibrary(); return null; };
  const probe = render(<Probe />);
  act(() => sync(null));
  probe.unmount();
});
afterEach(async () => { cleanup(); vi.restoreAllMocks(); await changeLocale('ru', { remember: false }); });

it('pokazyvaet korotkij katalog i delaet kazhduyu statyu dostupnoj cherez rubriku v SSR', () => {
  render(app());
  expect(document.querySelectorAll('.article-topic-grid a')).toHaveLength(7);
  expect(document.querySelectorAll('.article-library-card')).toHaveLength(6);
  for (const locale of ['ru', 'en']) {
    const articles = getArticles(locale);
    const groups = getArticleClusters(locale);
    const grouped = groups.flatMap((cluster) => articles.filter((article) => article.cluster === cluster.id));
    expect(new Set(grouped.map((article) => article.id)).size).toBe(articles.length);
  }
  for (const cluster of getArticleClusters('ru')) {
    const html = renderToString(app(getClusterPath(cluster, 'ru')));
    for (const article of getArticles('ru').filter((item) => item.cluster === cluster.id)) {
      expect(html).toContain(`href="${getArticlePath(article, 'ru')}"`);
    }
  }
});

it('nahodit statyu po zapomnennomu slovu i sbrasyvaet pustoj rezultat', () => {
  render(app());
  const input = screen.getByRole('searchbox');
  fireEvent.change(input, { target: { value: 'ЛЮКСМЕТР' } });
  expect(screen.getByRole('link', { name: light.title })).toBeInTheDocument();
  expect(document.querySelector('.article-topic-grid')).toBeNull();
  fireEvent.change(input, { target: { value: 'несуществующийзапрос' } });
  expect(screen.getByText(/По этому запросу ничего не нашлось/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Очистить поиск' }));
  expect(document.querySelectorAll('.article-topic-grid a')).toHaveLength(7);
});

it('sohranyaet statyu, vosstanavlivaet v drugoj lokali i snymaet zakladku', async () => {
  const page = render(app());
  fireEvent.click(screen.getByRole('button', { name: `Сохранить статью: ${light.title}` }));
  expect(JSON.parse(localStorage.getItem(key)).saved).toEqual([lightId]);
  fireEvent.click(screen.getByRole('link', { name: /^Сохранённые/ }));
  expect(document.querySelectorAll('.article-library-card')).toHaveLength(1);
  page.unmount();
  await changeLocale('en', { remember: false });
  render(app('/en/articles/?view=saved'));
  const translated = getArticles('en').find((article) => article.id === lightId);
  expect(screen.getByRole('link', { name: translated.title })).toHaveAttribute('href', getArticlePath(translated, 'en'));
  fireEvent.click(screen.getByRole('button', { name: `Saved: ${translated.title}` }));
  expect(screen.getByText(/Tap the bookmark/)).toBeInTheDocument();
});

it('zapominaet otkrytyj tekst odin raz i ochishchaet istoriyu bez poteri zakladok', () => {
  render(app(getArticlePath(light, 'ru')));
  expect(JSON.parse(localStorage.getItem(key)).recent).toEqual([lightId]);
  fireEvent.click(screen.getByRole('button', { name: `Сохранить статью: ${light.title}` }));
  act(() => rememberArticle(lightId));
  fireEvent.click(screen.getByRole('link', { name: 'Недавно открывали' }));
  expect(document.querySelectorAll('.article-library-card')).toHaveLength(1);
  fireEvent.click(screen.getByRole('button', { name: 'Очистить историю' }));
  expect(JSON.parse(localStorage.getItem(key))).toEqual({ saved: [lightId], recent: [] });
});

it('sinhroniziruet vkladki, ubiraet neizvestnye ID i ogranichivaet istoriyu', () => {
  render(app('/articles/?view=saved'));
  act(() => sync(JSON.stringify({ saved: [lightId, lightId, 'unknown'], recent: getArticles('ru').map((article) => article.id) })));
  expect(document.querySelectorAll('.article-library-card')).toHaveLength(1);
  fireEvent.click(screen.getByRole('link', { name: /Недавно открывали/ }));
  expect(document.querySelectorAll('.article-library-card')).toHaveLength(12);
});

it('obrabatyvaet povrezhdennoe ili zapreshchennoe hranilishche bez poteri teksta', () => {
  render(app());
  act(() => sync('{broken'));
  expect(screen.getByRole('status')).toHaveTextContent('Браузер не разрешает постоянное хранение');
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('blocked', 'SecurityError'); });
  fireEvent.click(screen.getByRole('button', { name: `Сохранить статью: ${light.title}` }));
  expect(screen.getByRole('button', { name: `Сохранено: ${light.title}` })).toHaveAttribute('aria-pressed', 'true');
  expect(document.querySelectorAll('.article-library-card')).toHaveLength(6);
});

it('gidratiruet pryamuyu ssylku na poisk bez rassinkhronizatsii staticheskogo HTML', async () => {
  const element = document.createElement('div');
  document.body.appendChild(element);
  element.innerHTML = renderToString(app());
  const recover = vi.fn();
  let root;
  try {
    await act(async () => { root = hydrateRoot(element, app('/articles/?q=люксметр'), { onRecoverableError: recover }); });
    expect(recover).not.toHaveBeenCalled();
    expect(within(element).getByRole('searchbox')).toHaveValue('люксметр');
    expect(within(element).getByRole('link', { name: light.title })).toBeInTheDocument();
  } finally {
    await act(async () => root?.unmount());
    element.remove();
  }
});
