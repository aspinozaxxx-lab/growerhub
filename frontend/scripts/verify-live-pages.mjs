import { SITE_URL } from '../src/domain/siteConfig.js';

const failures = [];
const origin = SITE_URL;

const assert = (condition, message) => {
  if (!condition) {
    failures.push(message);
  }
};

const request = async (url, options = {}) => fetch(url, {
  redirect: 'manual',
  signal: AbortSignal.timeout(15000),
  headers: { 'user-agent': 'GrowerHub SEO verifier' },
  ...options,
});

const sitemapResponse = await request(`${origin}/sitemap.xml`);
assert(sitemapResponse.status === 200, `Sitemap status: ${sitemapResponse.status}`);
const sitemap = await sitemapResponse.text();
const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((match) => match[1]);
assert(urls.length === 63, `Live sitemap: expected 63 URLs, got ${urls.length}`);

const internalUrls = new Set();

const checkPage = async (url) => {
  try {
    const response = await request(url);
    assert(response.status === 200, `Page status ${response.status}: ${url}`);
    assert(!response.headers.get('location'), `Unexpected redirect: ${url}`);
    const html = await response.text();
    const canonical = html.match(/<link rel="canonical" href="([^"]+)"/i)?.[1];
    assert(canonical === url, `Canonical mismatch: ${url} -> ${canonical || 'missing'}`);

    for (const match of html.matchAll(/href="([^"]+)"/g)) {
      const href = match[1];
      if (!href.startsWith('/') || href.startsWith('//')) {
        continue;
      }
      const target = new URL(href, origin);
      target.hash = '';
      const pathname = target.pathname;
      const hasExtension = pathname.split('/').at(-1).includes('.');
      assert(pathname === '/' || hasExtension || pathname.endsWith('/'), `Internal redirect link on ${url}: ${href}`);
      internalUrls.add(target.href);
    }
  } catch (error) {
    failures.push(`Request failed ${url}: ${error.message}`);
  }
};

for (let index = 0; index < urls.length; index += 6) {
  await Promise.all(urls.slice(index, index + 6).map(checkPage));
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
for (let index = 0; index < internalList.length; index += 10) {
  await Promise.all(internalList.slice(index, index + 10).map(checkInternal));
}

const redirectCases = [
  ['http://growerhub.ru/', `${origin}/`],
  ['https://www.growerhub.ru/', `${origin}/`],
  ['http://www.growerhub.ru/articles/home-assistant-dlya-rasteniy', `${origin}/articles/home-assistant-dlya-rasteniy/`],
  [`${origin}/articles/home-assistant-dlya-rasteniy`, `${origin}/articles/home-assistant-dlya-rasteniy/`],
  [`${origin}/app`, `${origin}/app/`],
];

for (const [source, expectedLocation] of redirectCases) {
  const response = await request(source);
  assert(response.status === 301, `Expected 301, got ${response.status}: ${source}`);
  assert(response.headers.get('location') === expectedLocation, `Redirect mismatch: ${source} -> ${response.headers.get('location')}`);
}

for (const pathname of ['/ne-sushchestvuet/', '/404.html']) {
  const response = await request(`${origin}${pathname}`);
  const html = await response.text();
  assert(response.status === 404, `Expected 404, got ${response.status}: ${pathname}`);
  assert(html.includes('noindex,nofollow'), `404 has no noindex,nofollow: ${pathname}`);
  assert(!html.includes('rel="canonical"'), `404 has canonical: ${pathname}`);
}

for (const pathname of ['/app/', '/app/plants/']) {
  const response = await request(`${origin}${pathname}`);
  const html = await response.text();
  assert(response.status === 200, `App fallback status ${response.status}: ${pathname}`);
  assert(html.includes('noindex,nofollow'), `App shell is indexable: ${pathname}`);
}

if (failures.length > 0) {
  console.error(failures.map((failure) => `- ${failure}`).join('\n'));
  process.exitCode = 1;
} else {
  console.log(`Live SEO verification passed: ${urls.length} pages, ${internalUrls.size} internal targets`);
}
