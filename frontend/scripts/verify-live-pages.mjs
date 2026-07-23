import { SITE_URL } from '../src/domain/siteConfig.js';

const failures = [];
const origin = SITE_URL;

const assert = (condition, message) => {
  if (!condition) failures.push(message);
};

const request = async (url, options = {}) => fetch(url, {
  redirect: 'manual',
  signal: AbortSignal.timeout(15000),
  headers: {
    'user-agent': 'GrowerHub SEO verifier',
    'accept-encoding': 'gzip',
  },
  ...options,
});

const sitemapResponse = await request(`${origin}/sitemap.xml`);
assert(sitemapResponse.status === 200, `Sitemap status: ${sitemapResponse.status}`);
const sitemap = await sitemapResponse.text();
const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((match) => match[1]);
const lastmods = [...sitemap.matchAll(/<lastmod>([^<]+)<\/lastmod>/g)].map((match) => match[1]);
const urlSet = new Set(urls);
assert(urls.length === 138, `Live sitemap: expected 138 URLs, got ${urls.length}`);
assert(urlSet.size === urls.length, 'Live sitemap contains duplicate URLs');
assert(lastmods.length === urls.length, 'Live sitemap: every URL must have lastmod');
assert(!/hreflang|xhtml:link/i.test(sitemap), 'Live sitemap must keep hreflang in HTML only');
assert(
  urls.filter((url) => new URL(url).pathname.startsWith('/en/')).length === 69,
  'Live sitemap does not contain 69 English URLs',
);

const internalUrls = new Set();
const checkPage = async (url) => {
  try {
    const response = await request(url);
    assert(response.status === 200, `Page status ${response.status}: ${url}`);
    assert(!response.headers.get('location'), `Unexpected redirect: ${url}`);
    const html = await response.text();
    const pathname = new URL(url).pathname;
    const locale = pathname.startsWith('/en/') ? 'en' : 'ru';
    const canonical = html.match(/<link rel="canonical" href="([^"]+)"/i)?.[1];
    const htmlLang = html.match(/<html lang="([^"]+)"/i)?.[1];
    const alternates = Object.fromEntries(
      [...html.matchAll(/<link rel="alternate" hreflang="([^"]+)" href="([^"]+)"/gi)]
        .map((match) => [match[1], match[2]]),
    );
    assert(canonical === url, `Canonical mismatch: ${url} -> ${canonical || 'missing'}`);
    assert(htmlLang === locale, `HTML lang mismatch: ${url} -> ${htmlLang || 'missing'}`);
    assert(urlSet.has(alternates.ru), `Missing RU alternate in sitemap: ${url}`);
    assert(urlSet.has(alternates.en), `Missing EN alternate in sitemap: ${url}`);
    assert(alternates['x-default'] === alternates.ru, `x-default mismatch: ${url}`);

    for (const match of html.matchAll(/<a\b[^>]*href="([^"]+)"/gi)) {
      const href = match[1];
      if (!href.startsWith('/') || href.startsWith('//')) continue;
      const target = new URL(href, origin);
      target.hash = '';
      const targetPath = target.pathname;
      const hasExtension = targetPath.split('/').at(-1).includes('.');
      assert(
        targetPath === '/' || hasExtension || targetPath.endsWith('/'),
        `Internal redirect link on ${url}: ${href}`,
      );
      internalUrls.add(target.href);
    }
  } catch (error) {
    failures.push(`Request failed ${url}: ${error.message}`);
  }
};

for (let index = 0; index < urls.length; index += 8) {
  await Promise.all(urls.slice(index, index + 8).map(checkPage));
}

const checkInternal = async (url) => {
  try {
    const response = await request(url, { method: 'HEAD' });
    assert(response.status === 200, `Internal target status ${response.status}: ${url}`);
    assert(!response.headers.get('location'), `Internal target redirects: ${url}`);
  } catch (error) {
    failures.push(`Internal target failed ${url}: ${error.message}`);
  }
};

const internalList = [...internalUrls];
for (let index = 0; index < internalList.length; index += 12) {
  await Promise.all(internalList.slice(index, index + 12).map(checkInternal));
}

const redirectCases = [
  ['http://growerhub.ru/', `${origin}/`],
  ['https://www.growerhub.ru/', `${origin}/`],
  ['http://www.growerhub.ru/en/articles/zigbee2mqtt-in-simple-words-how-the-sensor-coordinator-and-mqtt-are-connected', `${origin}/en/articles/zigbee2mqtt-in-simple-words-how-the-sensor-coordinator-and-mqtt-are-connected/`],
  [`${origin}/articles/home-assistant-dlya-rasteniy`, `${origin}/articles/home-assistant-dlya-rasteniy/`],
  [`${origin}/en`, `${origin}/en/`],
  [`${origin}/en/farm-automation`, `${origin}/en/farm-automation/`],
  [`${origin}/en/equipment/sensors`, `${origin}/en/equipment/sensors/`],
  [`${origin}/app`, `${origin}/app/`],
];

for (const [source, expectedLocation] of redirectCases) {
  const response = await request(source);
  assert(response.status === 301, `Expected 301, got ${response.status}: ${source}`);
  assert(
    response.headers.get('location') === expectedLocation,
    `Redirect mismatch: ${source} -> ${response.headers.get('location')}`,
  );
}

for (const [pathname, expectedText] of [
  ['/ne-sushchestvuet/', 'Страница не найдена'],
  ['/en/not-found/', 'Page not found'],
  ['/404.html', 'Страница не найдена'],
  ['/en/404.html', 'Page not found'],
]) {
  const response = await request(`${origin}${pathname}`);
  const html = await response.text();
  assert(response.status === 404, `Expected 404, got ${response.status}: ${pathname}`);
  assert(html.includes('noindex,nofollow'), `404 has no noindex,nofollow: ${pathname}`);
  assert(!html.includes('rel="canonical"'), `404 has canonical: ${pathname}`);
  assert(html.includes(expectedText), `Wrong localized 404: ${pathname}`);
}

for (const pathname of ['/app/', '/app/plants/']) {
  const response = await request(`${origin}${pathname}`);
  const html = await response.text();
  assert(response.status === 200, `App fallback status ${response.status}: ${pathname}`);
  assert(html.includes('noindex,nofollow'), `App shell is indexable: ${pathname}`);
}

const compressedPage = await request(`${origin}/en/`);
assert(
  compressedPage.headers.get('content-encoding') === 'gzip',
  'English HTML is not served with gzip',
);
const assetPath = (await compressedPage.text())
  .match(/<script type="module"[^>]+src="([^"]+)"/i)?.[1];
if (assetPath) {
  const assetResponse = await request(`${origin}${assetPath}`, { method: 'HEAD' });
  const cacheControl = assetResponse.headers.get('cache-control') || '';
  assert(/immutable/i.test(cacheControl), 'Hashed asset cache is not immutable');
  assert(/max-age=31536000/i.test(cacheControl), 'Hashed asset cache lifetime is not one year');
} else {
  assert(false, 'English page has no initial asset');
}

if (failures.length > 0) {
  console.error(failures.map((failure) => `- ${failure}`).join('\n'));
  process.exitCode = 1;
} else {
  console.log(
    `Live SEO verification passed: ${urls.length} pages, ${internalUrls.size} internal targets`,
  );
}
