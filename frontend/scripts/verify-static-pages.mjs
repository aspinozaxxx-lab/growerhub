import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import homeContent from '../content/pages/home.json' with { type: 'json' };
import miniFarmContent from '../content/pages/mini-farm.json' with { type: 'json' };
import { SITE_URL, TELEGRAM_DIRECT_URL } from '../src/domain/siteConfig.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DIST_DIR = path.join(ROOT, 'dist');
const read = (target) => fs.readFileSync(target, 'utf8').replace(/^\ufeff/, '');
const failures = [];
const deprecatedPositioning = [
  { pattern: /\bбет(?:а|ы|е|у|ой|ою|ам|ами|ах)\b/iu, label: 'бета' },
  { pattern: /\bbeta\b/iu, label: 'beta' },
  { pattern: /\bSLA\b/u, label: 'SLA' },
  { pattern: /24\/7/u, label: '24/7' },
  { pattern: /фиксированного срока ответа/iu, label: 'фиксированный срок ответа' },
  { pattern: /без обещания/iu, label: 'без обещания' },
];

const assert = (condition, message) => {
  if (!condition) {
    failures.push(message);
  }
};

const sitemap = read(path.join(DIST_DIR, 'sitemap.xml'));
const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((match) => match[1]);
assert(urls.length === 69, `Sitemap: expected 69 URLs, got ${urls.length}`);
assert(new Set(urls).size === urls.length, 'Sitemap: duplicate URLs found');

const urlToFile = (url) => {
  const pathname = new URL(url).pathname;
  if (pathname === '/') {
    return path.join(DIST_DIR, 'index.html');
  }
  return path.join(DIST_DIR, ...pathname.split('/').filter(Boolean), 'index.html');
};

for (const url of urls) {
  const pathname = new URL(url).pathname;
  const filePath = urlToFile(url);
  assert(pathname === '/' || pathname.endsWith('/'), `Sitemap URL has no trailing slash: ${url}`);
  assert(fs.existsSync(filePath), `Missing HTML for sitemap URL: ${url}`);
  if (!fs.existsSync(filePath)) {
    continue;
  }

  const html = read(filePath);
  const canonical = html.match(/<link rel="canonical" href="([^"]+)"/i)?.[1];
  assert(canonical === url, `Canonical mismatch: ${url} -> ${canonical || 'missing'}`);
  assert(/<h1[ >]/i.test(html), `Missing H1: ${url}`);
  assert(html.includes('data-platform-placement='), `Missing primary platform CTA: ${url}`);
}

const publicHtmlFiles = [];
const collectHtml = (directory) => {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== 'app') {
        collectHtml(target);
      }
    } else if (entry.name.endsWith('.html')) {
      publicHtmlFiles.push(target);
    }
  }
};
collectHtml(DIST_DIR);

for (const filePath of publicHtmlFiles) {
  const html = read(filePath);
  const hrefs = [...html.matchAll(/href="([^"]+)"/g)].map((match) => match[1]);
  const relativePath = path.relative(DIST_DIR, filePath);

  for (const item of deprecatedPositioning) {
    assert(!item.pattern.test(html), `Deprecated positioning "${item.label}" in ${relativePath}`);
  }

  for (const href of hrefs) {
    if (!href.startsWith('/') || href.startsWith('//')) {
      continue;
    }

    const pathname = href.split(/[?#]/, 1)[0];
    const hasExtension = path.posix.basename(pathname).includes('.');
    assert(pathname === '/' || hasExtension || pathname.endsWith('/'), `Internal redirect link in ${path.relative(DIST_DIR, filePath)}: ${href}`);

    if (pathname.startsWith('/app/') || pathname === '/app/') {
      continue;
    }

    const target = pathname === '/'
      ? path.join(DIST_DIR, 'index.html')
      : hasExtension
        ? path.join(DIST_DIR, ...pathname.split('/').filter(Boolean))
        : path.join(DIST_DIR, ...pathname.split('/').filter(Boolean), 'index.html');
    assert(fs.existsSync(target), `Broken internal link in ${path.relative(DIST_DIR, filePath)}: ${href}`);
  }
}

const homeHtml = read(path.join(DIST_DIR, 'index.html'));
const landingHtml = read(path.join(DIST_DIR, 'avtomatizatsiya-mini-fermy', 'index.html'));
assert(homeHtml.includes(homeContent.hero.title), 'Static home differs from shared home content');
assert(homeHtml.includes('GrowerHub · ранний доступ открыт'), 'Home has no early-access positioning');
assert(homeHtml.includes('большого практического опыта автоматизации теплиц'), 'Home has no experience positioning');
assert(landingHtml.includes(miniFarmContent.title), 'Static landing differs from shared landing content');
assert(landingHtml.includes(TELEGRAM_DIRECT_URL), 'Landing has no direct Telegram URL');

for (const pathname of [
  '/kak-nachat/',
  '/oborudovanie/',
  '/oborudovanie/zigbee-koordinator/',
  '/oborudovanie/datchiki/',
  '/oborudovanie/zigbee-rozetki/',
  '/oborudovanie/nasos-dlya-poliva/',
]) {
  assert(urls.includes(`${SITE_URL}${pathname}`), `New public route missing from sitemap: ${pathname}`);
}

for (const pathname of ['/privacy/', '/terms/']) {
  const html = read(path.join(DIST_DIR, pathname.slice(1, -1), 'index.html'));
  assert(html.includes('noindex,follow'), `${pathname} is not noindex,follow`);
  assert(!urls.includes(`${SITE_URL}${pathname}`), `${pathname} must not be in sitemap`);
}

for (const pathname of [
  '/oborudovanie/zigbee-koordinator/',
  '/oborudovanie/datchiki/',
  '/oborudovanie/zigbee-rozetki/',
]) {
  const html = read(urlToFile(`${SITE_URL}${pathname}`));
  assert(/ozon\.ru[^>]+rel="nofollow noreferrer"/i.test(html), `Store links are not nofollow: ${pathname}`);
  assert(!html.includes('"@type":"Product"'), `Equipment page has fake Product schema: ${pathname}`);
  assert(!html.includes('"@type":"Offer"'), `Equipment page has fake Offer schema: ${pathname}`);
}

const notFoundHtml = read(path.join(DIST_DIR, '404.html'));
assert(notFoundHtml.includes('noindex,nofollow'), '404 is not noindex,nofollow');
assert(!notFoundHtml.includes('rel="canonical"'), '404 must not have canonical');

const appHtml = read(path.join(DIST_DIR, 'app', 'index.html'));
assert(appHtml.includes('noindex,nofollow'), 'App shell is not noindex,nofollow');

const initialScript = homeHtml.match(/<script type="module"[^>]+src="([^"]+)"/i)?.[1];
assert(Boolean(initialScript), 'Public page has no initial module script');
assert(!homeHtml.includes('AppSection-'), 'Public HTML preloads the application chunk');
if (initialScript) {
  const bundle = read(path.join(DIST_DIR, ...initialScript.split('/').filter(Boolean)));
  for (const marker of ['AdminAutomation', 'AdminFarmDashboard', 'AppOnboarding', 'ResponsiveContainer', 'SensorStatsSidebar', 'recharts']) {
    assert(!bundle.includes(marker), `Public bundle contains application marker: ${marker}`);
  }
}

const articleFiles = urls
  .filter((url) => new URL(url).pathname.match(/^\/articles\/[^/]+\/$/))
  .map(urlToFile);
for (const filePath of articleFiles) {
  const html = read(filePath);
  assert(html.includes('"author":{"@type":"Organization"'), `BlogPosting has no Organization author: ${path.relative(DIST_DIR, filePath)}`);
}

if (failures.length > 0) {
  console.error(failures.map((failure) => `- ${failure}`).join('\n'));
  process.exitCode = 1;
} else {
  console.log(`SEO verification passed: ${urls.length} sitemap URLs, ${publicHtmlFiles.length} public HTML files`);
}
