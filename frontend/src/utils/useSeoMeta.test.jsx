import { StrictMode } from 'react';
import { cleanup, renderHook } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import useSeoMeta from './useSeoMeta';

afterEach(() => {
  cleanup();
  document.head.replaceChildren();
});

it('zamenyaet staticheskuyu razmetku pri gidratacii, vhode v kabinet i vozvrate na sajt', () => {
  document.head.innerHTML = `
    <title>Журнал полива — GrowerHub</title>
    <meta name="robots" content="index,follow">
    <meta name="twitter:title" content="Журнал полива — GrowerHub">
    <meta name="twitter:description" content="Старая статья">
    <meta name="twitter:image" content="https://growerhub.ru/old-image.png">
    <link rel="canonical" href="https://growerhub.ru/articles/watering-log/">
    <meta property="og:url" content="https://growerhub.ru/articles/watering-log/">
    <link rel="alternate" hreflang="ru" href="https://growerhub.ru/articles/watering-log/" data-growerhub-hreflang="true">
    <link rel="alternate" hreflang="en" href="https://growerhub.ru/en/articles/watering-log/" data-growerhub-hreflang="true">
    <link rel="alternate" hreflang="x-default" href="https://growerhub.ru/articles/watering-log/" data-growerhub-hreflang="true">
    <script type="application/ld+json" data-growerhub-jsonld="static">{"@type":"Article","headline":"История полива"}</script>
    <script type="application/ld+json" data-growerhub-jsonld="static">{"@type":"BreadcrumbList","itemListElement":[]}</script>
    <script type="application/json" id="growerhub-article">{"slug":"watering-log"}</script>
  `;
  const article = {
    title: 'Журнал полива — GrowerHub',
    description: 'История полива',
    path: '/articles/watering-log/',
    type: 'article',
    locale: 'ru',
    alternatePaths: { ru: '/articles/watering-log/', en: '/en/articles/watering-log/' },
    jsonLd: [{ '@type': 'Article', headline: 'История полива' }],
  };
  const { rerender } = renderHook((props) => useSeoMeta(props), {
    initialProps: article,
    wrapper: StrictMode,
  });
  expect(document.head.querySelectorAll('script[type="application/ld+json"]')).toHaveLength(2);
  expect(document.head.querySelectorAll('link[hreflang]')).toHaveLength(3);
  expect(document.head.querySelector('script[type="application/ld+json"]').textContent).toContain('История полива');
  rerender({ ...article, description: 'История полива и наблюдения' });
  expect(document.head.querySelectorAll('script[type="application/ld+json"]')).toHaveLength(2);

  rerender({
    title: 'Ручной полив — Демоферма · GrowerHub',
    description: 'Ручной полив',
    path: null,
    robots: 'noindex,nofollow',
    locale: 'ru',
  });
  expect(document.title).toBe('Ручной полив — Демоферма · GrowerHub');
  expect(document.head.querySelector('meta[name="robots"]').content).toBe('noindex,nofollow');
  expect(document.head.querySelector('link[rel="canonical"]')).toBeNull();
  expect(document.head.querySelector('meta[property="og:url"]')).toBeNull();
  expect(document.head.querySelectorAll('link[hreflang], script[type="application/ld+json"]')).toHaveLength(0);
  expect(document.head.querySelector('meta[property="og:type"]').content).toBe('website');
  expect(document.head.querySelector('meta[name="twitter:title"]').content).toBe(document.title);
  expect(document.head.querySelector('meta[name="twitter:description"]').content).toBe('Ручной полив');
  expect(document.head.querySelector('meta[name="twitter:image"]').content).toBe('https://growerhub.ru/og-growerhub.svg');
  expect(document.getElementById('growerhub-article')).not.toBeNull();

  rerender({ ...article, title: 'Watering log — GrowerHub', path: '/en/articles/watering-log/', locale: 'en' });
  expect(document.documentElement.lang).toBe('en');
  expect(document.head.querySelector('meta[name="robots"]').content).toBe('index,follow');
  expect(document.head.querySelector('link[rel="canonical"]').href).toBe('https://growerhub.ru/en/articles/watering-log/');
  expect(document.head.querySelector('meta[property="og:url"]').content).toBe('https://growerhub.ru/en/articles/watering-log/');
  expect(document.head.querySelectorAll('link[hreflang]')).toHaveLength(3);
  expect(document.head.querySelectorAll('script[type="application/ld+json"]')).toHaveLength(1);
  expect(JSON.parse(document.head.querySelector('script[type="application/ld+json"]').textContent).inLanguage).toBe('en');
});
