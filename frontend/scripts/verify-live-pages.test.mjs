// @vitest-environment node
import fs from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('node:fs', () => ({
  default: { existsSync: vi.fn(), readFileSync: vi.fn() },
}));

const origin = 'https://growerhub.ru';
const pairs = [['/', '/en/'], ['/articles/sensor/', '/en/articles/sensor/']];
const catalog = pairs.flat().map((pathname) => `${origin}${pathname}`);
const sitemap = (urls) => `<urlset>${urls.map((url) => (
  `<url><loc>${url}</loc><lastmod>2026-10-04</lastmod></url>`
)).join('')}</urlset>`;
let liveUrls;
let wrongCanonical;
let errors;
let logs;
let previousExitCode;

const mockRequest = async (url) => {
  const parsed = new URL(url);
  const pathname = parsed.pathname;
  if (pathname === '/sitemap.xml') return new Response(sitemap(liveUrls));
  if (parsed.origin !== origin || !pathname.endsWith('/') && !pathname.split('/').at(-1).includes('.')) {
    return new Response('', {
      status: 301,
      headers: { location: `${origin}${pathname.endsWith('/') ? pathname : `${pathname}/`}` },
    });
  }
  if (['/ne-sushchestvuet/', '/en/not-found/', '/404.html', '/en/404.html'].includes(pathname)) {
    return new Response(`<meta content="noindex,nofollow">${pathname.startsWith('/en/') ? 'Page not found' : 'Страница не найдена'}`, { status: 404 });
  }
  if (pathname.startsWith('/app/')) {
    return new Response('<meta content="noindex,nofollow">', { headers: { 'cache-control': 'no-cache' } });
  }
  if (pathname.startsWith('/assets/')) {
    return new Response('', { headers: { 'cache-control': 'public, max-age=31536000, immutable' } });
  }
  const locale = pathname.startsWith('/en/') ? 'en' : 'ru';
  const [ru, en] = pairs.find((pair) => pair.includes(pathname)) || pairs[1];
  return new Response(`<html lang="${locale}"><head>
    <link rel="canonical" href="${url === wrongCanonical ? `${origin}/wrong/` : url}">
    <link rel="alternate" hreflang="ru" href="${origin}${ru}">
    <link rel="alternate" hreflang="en" href="${origin}${en}">
    <link rel="alternate" hreflang="x-default" href="${origin}${ru}">
    <script type="module" src="/assets/public-test.js"></script>
    </head></html>`, { headers: { 'content-encoding': 'gzip' } });
};

beforeEach(() => {
  vi.resetModules();
  previousExitCode = process.exitCode;
  process.exitCode = undefined;
  liveUrls = [...catalog];
  wrongCanonical = null;
  fs.existsSync.mockReturnValue(true);
  fs.readFileSync.mockReturnValue(sitemap(catalog));
  errors = vi.spyOn(console, 'error').mockImplementation(() => {});
  logs = vi.spyOn(console, 'log').mockImplementation(() => {});
  vi.stubGlobal('fetch', vi.fn(mockRequest));
});

afterEach(() => {
  process.exitCode = previousExitCode;
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

const verify = () => import('./verify-live-pages.mjs');
const reportedErrors = () => errors.mock.calls.flat().join('\n');

describe('Live public catalog verification', () => {
  it.each([2, 4])('accepts the current built catalog with %i URLs in any order', async (count) => {
    const urls = catalog.slice(0, count);
    fs.readFileSync.mockReturnValue(sitemap(urls));
    liveUrls = [...urls].reverse();
    await verify();
    expect(errors).not.toHaveBeenCalled();
    expect(process.exitCode).toBeUndefined();
    expect(logs).toHaveBeenCalledWith(expect.stringContaining(`${urls.length} pages`));
  });

  it('detects a missing whole RU/EN pair even when remaining alternates are valid', async () => {
    liveUrls = catalog.slice(0, 2);
    await verify();
    expect(process.exitCode).toBe(1);
    for (const url of catalog.slice(2)) {
      expect(reportedErrors()).toContain(`Missing live sitemap URL from built catalog: ${url}`);
    }
  });

  it('detects a replaced English page when total and locale counts still match', async () => {
    const unexpectedUrl = `${origin}/en/articles/unexpected/`;
    liveUrls[liveUrls.length - 1] = unexpectedUrl;
    await verify();
    expect(process.exitCode).toBe(1);
    expect(reportedErrors()).toContain(`Missing live sitemap URL from built catalog: ${catalog.at(-1)}`);
    expect(reportedErrors()).toContain(`Unexpected live sitemap URL outside built catalog: ${unexpectedUrl}`);
  });

  it('keeps detecting duplicate live URLs', async () => {
    liveUrls.push(catalog[0]);
    await verify();
    expect(process.exitCode).toBe(1);
    expect(reportedErrors()).toContain('Live sitemap contains duplicate URLs');
  });

  it('keeps checking canonical URLs when the sitemap matches', async () => {
    wrongCanonical = catalog.at(-1);
    await verify();
    expect(process.exitCode).toBe(1);
    expect(reportedErrors()).toContain(`Canonical mismatch: ${wrongCanonical}`);
  });

  it('requires a local build before contacting production', async () => {
    fs.existsSync.mockReturnValue(false);
    await expect(verify()).rejects.toThrow('Run npm run build');
    expect(fetch).not.toHaveBeenCalled();
  });

  it('rejects an empty built catalog before contacting production', async () => {
    fs.readFileSync.mockReturnValue('<urlset></urlset>');
    await expect(verify()).rejects.toThrow('Built sitemap has no URLs');
    expect(fetch).not.toHaveBeenCalled();
  });
});
