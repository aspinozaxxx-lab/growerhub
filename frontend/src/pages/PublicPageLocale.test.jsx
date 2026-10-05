import { createElement } from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import AboutPage from './AboutPage';
import ArticlesListPage from './ArticlesListPage';
import MiniFarmPage from './MiniFarmPage';
import PumpEarlyAccessPage from './PumpEarlyAccessPage';
import { getArticleClusters } from '../content/articleClusters';
import { changeLocale } from '../locales/i18n';

vi.mock('../components/LeadCta', () => ({ default: () => null }));
vi.mock('../components/PlatformStartLink', () => ({ default: () => null }));
vi.mock('../utils/analytics', () => ({ trackProductGoal: vi.fn() }));

afterEach(async () => { cleanup(); await changeLocale('ru', { remember: false }); });

it.each([
  { name: 'about', component: AboutPage }, { name: 'articles', component: ArticlesListPage },
  { name: 'farm', component: MiniFarmPage }, { name: 'pump', component: PumpEarlyAccessPage },
])('kontent $name vybiraet yazyk pri kazhdom rendere, ne pri importe', async ({ component }) => {
  await changeLocale('ru', { remember: false });
  const view = render(<MemoryRouter>{createElement(component)}</MemoryRouter>);
  expect(document.body.textContent).toMatch(/[\u0400-\u04ff]/u);
  await changeLocale('en', { remember: false });
  view.rerender(<MemoryRouter>{createElement(component)}</MemoryRouter>);
  expect(document.body.textContent).not.toMatch(/[\u0400-\u04ff]/u);
});

it('anglijskie ssylki na klastery ispolzuyut anglijskie slug', async () => {
  await changeLocale('en', { remember: false });
  render(<MemoryRouter><ArticlesListPage /></MemoryRouter>);
  const hrefs = [...document.querySelectorAll('a[href*="/clusters/"]')].map((link) => link.getAttribute('href'));
  expect(hrefs).toEqual(getArticleClusters('en').map((cluster) => `/en/articles/clusters/${cluster.slug}/`));
});

it('kontakt sajta v about sohranyaet anglijskij razdel', async () => {
  await changeLocale('en', { remember: false });
  render(<MemoryRouter><AboutPage /></MemoryRouter>);
  expect(screen.getByRole('link', { name: 'https://growerhub.ru/en/' })).toHaveAttribute('href', 'https://growerhub.ru/en/');
  expect(document.querySelector('a[href="https://growerhub.ru/"]')).toBeNull();
});
