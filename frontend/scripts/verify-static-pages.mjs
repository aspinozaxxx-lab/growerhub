import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import enHomeContent from '../content/en/pages/home.json' with { type: 'json' };
import enMiniFarmContent from '../content/en/pages/mini-farm.json' with { type: 'json' };
import enEquipment from '../content/en/equipment/catalog.json' with { type: 'json' };
import ruEquipment from '../content/equipment/catalog.json' with { type: 'json' };
import ruHomeContent from '../content/pages/home.json' with { type: 'json' };
import ruMiniFarmContent from '../content/pages/mini-farm.json' with { type: 'json' };
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
  if (!condition) failures.push(message);
};

const exactEquipmentModels = new Set([
  'SONOFF ZBDongle-P',
  'SONOFF ZBDongle-E',
  'SONOFF SNZB-02P',
  'SONOFF SNZB-02D',
  'Aqara WSDCGQ11LM',
  'Aqara SJCGQ11LM',
  'SONOFF S26R2ZB',
  'SONOFF S60ZBTPF',
  'Aqara SP-EUC01',
]);
const equipmentByLocale = { ru: ruEquipment, en: enEquipment };
for (const [locale, catalog] of Object.entries(equipmentByLocale)) {
  const items = Object.values(catalog.categories).flatMap((category) => category.items);
  assert(items.length === 13, `Equipment ${locale}: expected 13 items, got ${items.length}`);
  assert(!items.some((item) => item.model === 'TS011F'), `Equipment ${locale}: TS011F returned`);
  for (const item of items) {
    assert(Boolean(item.image), `Equipment ${locale}: no image for ${item.model}`);
    assert(Boolean(item.image_source_url), `Equipment ${locale}: no image source for ${item.model}`);
    assert(Boolean(item.image_license), `Equipment ${locale}: no image license for ${item.model}`);
    const imagePath = path.join(DIST_DIR, ...item.image.split('/').filter(Boolean));
    assert(fs.existsSync(imagePath), `Equipment ${locale}: missing local image for ${item.model}`);
    if (exactEquipmentModels.has(item.model)) {
      assert(
        !item.image.endsWith('.svg'),
        `Equipment ${locale}: exact model uses a generic image: ${item.model}`,
      );
    }
  }
}

const sitemap = read(path.join(DIST_DIR, 'sitemap.xml'));
const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((match) => match[1]);
const lastmods = [...sitemap.matchAll(/<lastmod>([^<]+)<\/lastmod>/g)].map((match) => match[1]);
const urlSet = new Set(urls);
const ruUrls = urls.filter((url) => !new URL(url).pathname.startsWith('/en/'));
const enUrls = urls.filter((url) => new URL(url).pathname.startsWith('/en/'));
assert(urls.length === 138, `Sitemap: expected 138 URLs, got ${urls.length}`);
assert(ruUrls.length === 69, `Sitemap: expected 69 RU URLs, got ${ruUrls.length}`);
assert(enUrls.length === 69, `Sitemap: expected 69 EN URLs, got ${enUrls.length}`);
assert(urlSet.size === urls.length, 'Sitemap: duplicate URLs found');
assert(lastmods.length === urls.length, 'Sitemap: every URL must have lastmod');
assert(
  lastmods.every((value) => /^\d{4}-\d{2}-\d{2}$/.test(value)),
  'Sitemap: lastmod must use YYYY-MM-DD',
);
assert(!/hreflang|xhtml:link/i.test(sitemap), 'Sitemap: hreflang must remain in HTML only');

const urlToFile = (url) => {
  const pathname = new URL(url).pathname;
  if (pathname === '/') return path.join(DIST_DIR, 'index.html');
  return path.join(DIST_DIR, ...pathname.split('/').filter(Boolean), 'index.html');
};

const extractAlternates = (html) => Object.fromEntries(
  [...html.matchAll(/<link rel="alternate" hreflang="([^"]+)" href="([^"]+)"/gi)]
    .map((match) => [match[1], match[2]]),
);

for (const url of urls) {
  const pathname = new URL(url).pathname;
  const locale = pathname.startsWith('/en/') ? 'en' : 'ru';
  const filePath = urlToFile(url);
  assert(pathname === '/' || pathname.endsWith('/'), `Sitemap URL has no trailing slash: ${url}`);
  assert(fs.existsSync(filePath), `Missing HTML for sitemap URL: ${url}`);
  if (!fs.existsSync(filePath)) continue;

  const html = read(filePath);
  const canonical = html.match(/<link rel="canonical" href="([^"]+)"/i)?.[1];
  const htmlLang = html.match(/<html lang="([^"]+)"/i)?.[1];
  const alternates = extractAlternates(html);
  assert(canonical === url, `Canonical mismatch: ${url} -> ${canonical || 'missing'}`);
  assert(htmlLang === locale, `HTML lang mismatch: ${url} -> ${htmlLang || 'missing'}`);
  assert(alternates.ru && urlSet.has(alternates.ru), `Missing sitemap RU alternate: ${url}`);
  assert(alternates.en && urlSet.has(alternates.en), `Missing sitemap EN alternate: ${url}`);
  assert(alternates['x-default'] === alternates.ru, `x-default mismatch: ${url}`);
  assert(alternates.ru !== alternates.en, `Identical locale alternates: ${url}`);
  assert(
    extractAlternates(read(urlToFile(alternates.ru))).en === alternates.en,
    `Non-reciprocal RU alternate: ${url}`,
  );
  assert(
    extractAlternates(read(urlToFile(alternates.en))).ru === alternates.ru,
    `Non-reciprocal EN alternate: ${url}`,
  );
  assert(/<h1[ >]/i.test(html), `Missing H1: ${url}`);
  assert(html.includes('data-platform-placement='), `Missing primary platform CTA: ${url}`);
  assert(
    html.includes(`property="og:locale" content="${locale === 'en' ? 'en_US' : 'ru_RU'}"`),
    `Open Graph locale mismatch: ${url}`,
  );

  const jsonLdBlocks = [...html.matchAll(
    /<script type="application\/ld\+json">([\s\S]*?)<\/script>/gi,
  )];
  assert(jsonLdBlocks.length > 0, `Missing JSON-LD: ${url}`);
  for (const block of jsonLdBlocks) {
    try {
      const jsonLd = JSON.parse(block[1]);
      assert(jsonLd.inLanguage === locale, `JSON-LD language mismatch: ${url}`);
    } catch {
      assert(false, `Invalid JSON-LD: ${url}`);
    }
  }

  if (locale === 'en') {
    const visibleHtml = html
      .replace(/<script[\s\S]*?<\/script>/gi, '')
      .replace(/<style[\s\S]*?<\/style>/gi, '');
    assert(!/[А-Яа-яЁё]/u.test(visibleHtml), `Cyrillic found on English page: ${url}`);
  }
}

const publicHtmlFiles = [];
const collectHtml = (directory) => {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== 'app' && entry.name !== 'assets') collectHtml(target);
    } else if (entry.name.endsWith('.html')) {
      publicHtmlFiles.push(target);
    }
  }
};
collectHtml(DIST_DIR);

for (const filePath of publicHtmlFiles) {
  const html = read(filePath);
  const relativePath = path.relative(DIST_DIR, filePath);
  for (const item of deprecatedPositioning) {
    assert(!item.pattern.test(html), `Deprecated positioning "${item.label}" in ${relativePath}`);
  }

  for (const match of html.matchAll(/<a\b([^>]*?)href="([^"]+)"([^>]*)>/gi)) {
    const [tag, before, href, after] = match;
    const attributes = `${before}${after}`;
    if (href.startsWith(`${SITE_URL}/`) || href === SITE_URL) {
      const targetPath = new URL(href).pathname;
      const currentEnglish = relativePath.startsWith(`en${path.sep}`);
      const targetEnglish = targetPath.startsWith('/en/');
      const isLanguageSwitch = /locale-switch/.test(attributes);
      assert(
        currentEnglish === targetEnglish || isLanguageSwitch,
        `Cross-locale link outside switch in ${relativePath}: ${tag}`,
      );
      continue;
    }
    if (!href.startsWith('/') || href.startsWith('//')) continue;

    const pathname = href.split(/[?#]/, 1)[0];
    const hasExtension = path.posix.basename(pathname).includes('.');
    assert(
      pathname === '/' || hasExtension || pathname.endsWith('/'),
      `Internal redirect link in ${relativePath}: ${href}`,
    );
    if (pathname.startsWith('/app/') || pathname === '/app/') continue;

    const target = pathname === '/'
      ? path.join(DIST_DIR, 'index.html')
      : hasExtension
        ? path.join(DIST_DIR, ...pathname.split('/').filter(Boolean))
        : path.join(DIST_DIR, ...pathname.split('/').filter(Boolean), 'index.html');
    assert(fs.existsSync(target), `Broken internal link in ${relativePath}: ${href}`);

    const currentEnglish = relativePath.startsWith(`en${path.sep}`);
    const targetEnglish = pathname.startsWith('/en/');
    const isLanguageSwitch = /locale-switch/.test(attributes);
    if (!pathname.startsWith('/app/')) {
      assert(
        currentEnglish === targetEnglish || isLanguageSwitch,
        `Cross-locale link outside switch in ${relativePath}: ${href}`,
      );
    }
  }
}

const ruHomeHtml = read(path.join(DIST_DIR, 'index.html'));
const enHomeHtml = read(path.join(DIST_DIR, 'en', 'index.html'));
const ruLandingHtml = read(path.join(DIST_DIR, 'avtomatizatsiya-mini-fermy', 'index.html'));
const enLandingHtml = read(path.join(DIST_DIR, 'en', 'farm-automation', 'index.html'));
assert(ruHomeHtml.includes(ruHomeContent.hero.title), 'RU static home differs from shared content');
assert(enHomeHtml.includes(enHomeContent.hero.title), 'EN static home differs from shared content');
assert(ruLandingHtml.includes(ruMiniFarmContent.title), 'RU landing differs from shared content');
assert(enLandingHtml.includes(enMiniFarmContent.title), 'EN landing differs from shared content');
assert(ruLandingHtml.includes(TELEGRAM_DIRECT_URL), 'RU landing has no direct Telegram URL');
assert(enLandingHtml.includes(TELEGRAM_DIRECT_URL), 'EN landing has no direct Telegram URL');
assert(
  enHomeHtml.includes('"areaServed":"Russia and CIS countries"'),
  'EN SoftwareApplication has no international areaServed',
);
assert(
  ruHomeHtml.includes('"areaServed":"Россия и страны СНГ"'),
  'RU SoftwareApplication has no Russia and CIS areaServed',
);

for (const pathname of ['/privacy/', '/terms/', '/en/privacy/', '/en/terms/']) {
  const html = read(urlToFile(`${SITE_URL}${pathname}`));
  assert(html.includes('noindex,follow'), `${pathname} is not noindex,follow`);
  assert(!urlSet.has(`${SITE_URL}${pathname}`), `${pathname} must not be in sitemap`);
}

for (const pathname of [
  '/oborudovanie/zigbee-koordinator/',
  '/oborudovanie/datchiki/',
  '/oborudovanie/zigbee-rozetki/',
]) {
  const html = read(urlToFile(`${SITE_URL}${pathname}`));
  assert(/ozon\.ru[^>]+rel="nofollow noreferrer"/i.test(html), `Ozon links are not nofollow: ${pathname}`);
  assert(!html.includes('"@type":"Product"'), `Equipment page has fake Product schema: ${pathname}`);
}
for (const pathname of [
  '/en/equipment/zigbee-coordinators/',
  '/en/equipment/sensors/',
  '/en/equipment/zigbee-smart-plugs/',
]) {
  const html = read(urlToFile(`${SITE_URL}${pathname}`));
  assert(!/ozon\.ru/i.test(html), `Ozon link found on English page: ${pathname}`);
  assert(!html.includes('"@type":"Product"'), `Equipment page has fake Product schema: ${pathname}`);
}

const equipmentImages = [...ruLandingHtml.matchAll(/<img[^>]+src="([^"]+)"/gi)];
assert(equipmentImages.length >= 4, 'RU landing has fewer than four synthetic screenshots');
for (const pathname of ['/404.html', '/en/404.html']) {
  const html = read(path.join(DIST_DIR, ...pathname.split('/').filter(Boolean)));
  assert(html.includes('noindex,nofollow'), `${pathname} is not noindex,nofollow`);
  assert(!html.includes('rel="canonical"'), `${pathname} must not have canonical`);
}

const appHtml = read(path.join(DIST_DIR, 'app', 'index.html'));
assert(appHtml.includes('noindex,nofollow'), 'App shell is not noindex,nofollow');

const initialScript = ruHomeHtml.match(/<script type="module"[^>]+src="([^"]+)"/i)?.[1];
assert(Boolean(initialScript), 'Public page has no initial module script');
if (initialScript) {
  const bundlePath = path.join(DIST_DIR, ...initialScript.split('/').filter(Boolean));
  const bundle = read(bundlePath);
  assert(fs.statSync(bundlePath).size <= 500 * 1024, 'Public entry exceeds 500 KiB');
  for (const marker of [
    'AdminAutomation',
    'AdminFarmDashboard',
    'AppOnboarding',
    'ResponsiveContainer',
    'SensorStatsSidebar',
    'recharts',
  ]) {
    assert(!bundle.includes(marker), `Public entry contains application marker: ${marker}`);
  }
}

const articleUrls = urls.filter((url) => (
  new URL(url).pathname.match(/^\/(?:en\/)?articles\/[^/]+\/$/)
));
assert(articleUrls.length === 108, `Expected 108 article pages, got ${articleUrls.length}`);
for (const url of articleUrls) {
  const html = read(urlToFile(url));
  assert(
    html.includes('"author":{"@type":"Organization"'),
    `BlogPosting has no Organization author: ${url}`,
  );
  assert(html.includes('id="growerhub-page-data"'), `Article has no static body payload: ${url}`);
}

if (failures.length > 0) {
  console.error(failures.map((failure) => `- ${failure}`).join('\n'));
  process.exitCode = 1;
} else {
  console.log(
    `SEO verification passed: ${urls.length} sitemap URLs, `
    + `${publicHtmlFiles.length} public HTML files`,
  );
}
