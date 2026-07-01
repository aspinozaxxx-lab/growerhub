import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import matter from 'gray-matter';
import { marked } from 'marked';
import { articleClusters } from '../src/content/articleClusters.js';

const SITE_URL = 'https://growerhub.ru';
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DIST_DIR = path.join(ROOT, 'dist');
const ARTICLES_DIR = path.join(ROOT, 'content', 'articles');
const PAGES_DIR = path.join(ROOT, 'content', 'pages');
const TEMPLATE_PATH = path.join(DIST_DIR, 'index.html');
const UTF8_BOM = '\ufeff';
const STATIC_LASTMOD = '2026-07-01';
const APP_NO_INDEX_ROUTES = [
  { path: '/app', title: 'GrowerHub - приложение', description: 'Личный кабинет GrowerHub для контроля растений, устройств, датчиков и полива.' },
  { path: '/app/login', title: 'Вход в GrowerHub', description: 'Страница входа в личный кабинет GrowerHub для пользователей системы ухода за растениями.' },
  { path: '/app/plants', title: 'Растения - GrowerHub', description: 'Приватный раздел GrowerHub со списком растений, журналом ухода и связанными устройствами.' },
  { path: '/app/devices', title: 'Устройства - GrowerHub', description: 'Приватный раздел GrowerHub для просмотра контроллеров, датчиков и состояния оборудования.' },
  { path: '/app/profile', title: 'Профиль - GrowerHub', description: 'Приватный раздел GrowerHub с настройками пользователя и данными учетной записи.' },
  { path: '/app/admin', title: 'Администрирование - GrowerHub', description: 'Закрытый административный раздел GrowerHub для управления пользователями, устройствами и сервисами.' },
  { path: '/app/admin/users', title: 'Пользователи - GrowerHub', description: 'Закрытый административный раздел GrowerHub для управления пользователями.' },
  { path: '/app/admin/devices', title: 'Устройства администрирования - GrowerHub', description: 'Закрытый административный раздел GrowerHub для управления устройствами.' },
  { path: '/app/admin/plants', title: 'Растения администрирования - GrowerHub', description: 'Закрытый административный раздел GrowerHub для управления растениями.' },
  { path: '/app/admin/mqtt', title: 'MQTT - GrowerHub', description: 'Закрытый административный раздел GrowerHub для просмотра сообщений MQTT.' },
  { path: '/app/admin/zigbee', title: 'Zigbee - GrowerHub', description: 'Закрытый административный раздел GrowerHub для управления Zigbee-устройствами.' },
  { path: '/app/admin/automation', title: 'Автоматизация - GrowerHub', description: 'Закрытый административный раздел GrowerHub для управления автоматизацией полива.' },
];

const htmlEscape = (value = '') =>
  String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const stripBom = (value) => value.replace(/^\ufeff/, '');

const normalizeArray = (value) => {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean);
  }
  if (!value) {
    return [];
  }
  return String(value)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
};

const writeText = (targetPath, content) => {
  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  fs.writeFileSync(targetPath, `${UTF8_BOM}${content}`, 'utf8');
};

const readTemplate = () => {
  if (!fs.existsSync(TEMPLATE_PATH)) {
    throw new Error(`Missing Vite template at ${TEMPLATE_PATH}`);
  }
  return stripBom(fs.readFileSync(TEMPLATE_PATH, 'utf8'));
};

const readArticles = () =>
  fs.readdirSync(ARTICLES_DIR)
    .filter((name) => name.endsWith('.md'))
    .sort()
    .map((name) => {
      const raw = stripBom(fs.readFileSync(path.join(ARTICLES_DIR, name), 'utf8'));
      const parsed = matter(raw);
      const data = parsed.data || {};

      return {
        slug: data.slug || path.basename(name, '.md'),
        title: data.title || path.basename(name, '.md'),
        summary: data.summary || '',
        created_at: data.created_at || '',
        updated_at: data.updated_at || data.created_at || '',
        cluster: data.cluster || '',
        tags: normalizeArray(data.tags),
        keywords: normalizeArray(data.keywords),
        related: normalizeArray(data.related),
        hero_image: data.hero_image || '',
        hero_alt: data.hero_alt || data.title || '',
        body: parsed.content.trim(),
        bodyHtml: marked.parse(parsed.content.trim()),
      };
    })
    .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));

const readJson = (name) =>
  JSON.parse(stripBom(fs.readFileSync(path.join(PAGES_DIR, name), 'utf8')));

const extractHeadAssets = (template) => {
  const head = template.match(/<head>([\s\S]*?)<\/head>/)?.[1] || '';
  return head
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) =>
      line.includes('/assets/') ||
      line.includes('rel="modulepreload"') ||
      line.includes('rel="stylesheet"'))
    .map((line) => `    ${line}`)
    .join('\n');
};

const replaceHead = (template, headContent) => template.replace(
  /<head>[\s\S]*?<\/head>/,
  `<head>\n${headContent}\n  </head>`,
);

const replaceRoot = (template, bodyContent) => template.replace(
  /<div id="root">[\s\S]*?<\/div>/,
  `<div id="root">${bodyContent}</div>`,
);

const makeMetaHead = ({
  title,
  description,
  canonical,
  type = 'website',
  image = '',
  robots = 'index,follow',
  jsonLd = [],
  assets = '',
}) => {
  const ogImage = image ? `${SITE_URL}${image}` : `${SITE_URL}/favicon.svg`;
  const jsonBlocks = jsonLd
    .filter(Boolean)
    .map((item) => `    <script type="application/ld+json">${JSON.stringify(item)}</script>`)
    .join('\n');

  return [
    '    <meta charset="UTF-8" />',
    '    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />',
    '    <meta name="viewport" content="width=device-width, initial-scale=1.0" />',
    `    <title>${htmlEscape(title)}</title>`,
    `    <meta name="description" content="${htmlEscape(description)}" />`,
    `    <meta name="robots" content="${htmlEscape(robots)}" />`,
    `    <link rel="canonical" href="${htmlEscape(canonical)}" />`,
    `    <meta property="og:title" content="${htmlEscape(title)}" />`,
    `    <meta property="og:description" content="${htmlEscape(description)}" />`,
    `    <meta property="og:type" content="${htmlEscape(type)}" />`,
    `    <meta property="og:url" content="${htmlEscape(canonical)}" />`,
    `    <meta property="og:image" content="${htmlEscape(ogImage)}" />`,
    '    <meta name="twitter:card" content="summary_large_image" />',
    `    <meta name="twitter:title" content="${htmlEscape(title)}" />`,
    `    <meta name="twitter:description" content="${htmlEscape(description)}" />`,
    `    <meta name="twitter:image" content="${htmlEscape(ogImage)}" />`,
    assets,
    jsonBlocks,
  ].filter(Boolean).join('\n');
};

const pageShell = (template, meta, mainHtml, assets) => replaceRoot(
  replaceHead(template, makeMetaHead({ ...meta, assets })),
  `\n    <main class="section static-page">\n${mainHtml}\n    </main>\n  `,
);

const appShell = (template, meta, assets) => replaceHead(
  template,
  makeMetaHead({ ...meta, robots: 'noindex,nofollow', assets }),
);

const formatDate = (date) => (date ? new Date(date).toLocaleDateString('ru-RU') : '');

const renderArticleCard = (article) => `
      <article class="article-card">
        <div class="article-meta">${htmlEscape(formatDate(article.created_at))}</div>
        <a href="/articles/${htmlEscape(article.slug)}">${htmlEscape(article.title)}</a>
        <p>${htmlEscape(article.summary)}</p>
      </article>`;

const breadcrumbLd = (items) => ({
  '@context': 'https://schema.org',
  '@type': 'BreadcrumbList',
  itemListElement: items.map((item, index) => ({
    '@type': 'ListItem',
    position: index + 1,
    name: item.name,
    item: item.url,
  })),
});

const articleLd = (article, cluster) => ({
  '@context': 'https://schema.org',
  '@type': 'BlogPosting',
  headline: article.title,
  description: article.summary,
  datePublished: article.created_at,
  dateModified: article.updated_at || article.created_at,
  image: article.hero_image ? `${SITE_URL}${article.hero_image}` : undefined,
  mainEntityOfPage: `${SITE_URL}/articles/${article.slug}`,
  articleSection: cluster?.title,
  keywords: article.keywords,
  publisher: {
    '@type': 'Organization',
    name: 'GrowerHub',
    url: SITE_URL,
  },
});

const collectionLd = (title, description, url, articles = []) => ({
  '@context': 'https://schema.org',
  '@type': 'CollectionPage',
  name: title,
  description,
  url,
  hasPart: articles.map((article) => ({
    '@type': 'Article',
    headline: article.title,
    url: `${SITE_URL}/articles/${article.slug}`,
  })),
});

const renderArticlePage = (template, assets, article, articlesBySlug, clustersBySlug) => {
  const cluster = clustersBySlug.get(article.cluster);
  const related = article.related.map((slug) => articlesBySlug.get(slug)).filter(Boolean).slice(0, 4);
  const canonical = `${SITE_URL}/articles/${article.slug}`;
  const mainHtml = `
      <div class="article-meta">Обновлено ${htmlEscape(formatDate(article.updated_at || article.created_at))}</div>
      <h1>${htmlEscape(article.title)}</h1>
      <p>${htmlEscape(article.summary)}</p>
      ${cluster ? `<p><a class="secondary-link" href="/articles/clusters/${htmlEscape(cluster.slug)}">${htmlEscape(cluster.title)}</a></p>` : ''}
      <div class="article-body">
${article.bodyHtml}
      </div>
      ${related.length ? `
      <section class="related-articles">
        <h2>Читайте также</h2>
        <div class="articles-list">
${related.map(renderArticleCard).join('\n')}
        </div>
      </section>` : ''}`;

  return pageShell(template, {
    title: `${article.title} - GrowerHub`,
    description: article.summary,
    canonical,
    type: 'article',
    image: article.hero_image,
    jsonLd: [
      articleLd(article, cluster),
      breadcrumbLd([
        { name: 'GrowerHub', url: SITE_URL },
        { name: 'Статьи', url: `${SITE_URL}/articles` },
        ...(cluster ? [{ name: cluster.title, url: `${SITE_URL}/articles/clusters/${cluster.slug}` }] : []),
        { name: article.title, url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderArticlesIndex = (template, assets, articlesByCluster) => {
  const canonical = `${SITE_URL}/articles`;
  const allArticles = [...articlesByCluster.values()].flat();
  const mainHtml = `
      <h1>Статьи GrowerHub</h1>
      <p>Практические материалы о поливе, датчиках, журнале ухода, Zigbee, Home Assistant и организации нескольких зон выращивания.</p>
      <div class="cluster-list">
${articleClusters.map((cluster) => `
        <section class="cluster-block">
          <div class="cluster-block__header">
            <div>
              <h2>${htmlEscape(cluster.title)}</h2>
              <p>${htmlEscape(cluster.description)}</p>
            </div>
            <a class="secondary-link" href="/articles/clusters/${htmlEscape(cluster.slug)}">Раздел</a>
          </div>
          <div class="cluster-meta-grid">
            <div><strong>Кому полезно</strong><p>${htmlEscape(cluster.persona)}</p></div>
            <div><strong>Практический смысл</strong><p>${htmlEscape(cluster.commercialIntent)}</p></div>
          </div>
          <div class="articles-list">
${(articlesByCluster.get(cluster.slug) || []).map(renderArticleCard).join('\n')}
          </div>
        </section>`).join('\n')}
      </div>`;

  return pageShell(template, {
    title: 'Статьи GrowerHub - практические материалы об уходе за растениями',
    description: 'Раздел статей GrowerHub: автополив, датчики, журнал ухода, Zigbee, Home Assistant и мини-ферма.',
    canonical,
    jsonLd: [
      collectionLd('Статьи GrowerHub', 'Практические материалы GrowerHub об уходе за растениями.', canonical, allArticles),
      breadcrumbLd([
        { name: 'GrowerHub', url: SITE_URL },
        { name: 'Статьи', url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderClusterPage = (template, assets, cluster, articles, otherClusters) => {
  const canonical = `${SITE_URL}/articles/clusters/${cluster.slug}`;
  const mainHtml = `
      <div class="article-meta">Практический раздел</div>
      <h1>${htmlEscape(cluster.title)}</h1>
      <p>${htmlEscape(cluster.description)}</p>
      <div class="cluster-meta-grid">
        <div><strong>Портрет пользователя</strong><p>${htmlEscape(cluster.persona)}</p></div>
        <div><strong>Практический смысл</strong><p>${htmlEscape(cluster.commercialIntent)}</p></div>
      </div>
      <div class="keyword-list">
${cluster.keywords.map((keyword) => `        <span>${htmlEscape(keyword)}</span>`).join('\n')}
      </div>
      <section class="cluster-block">
        <h2>Статьи раздела</h2>
        <div class="articles-list">
${articles.map(renderArticleCard).join('\n')}
        </div>
      </section>
      <section class="cluster-block">
        <h2>Другие разделы</h2>
        <div class="cluster-nav-grid">
${otherClusters.map((item) => `          <a href="/articles/clusters/${htmlEscape(item.slug)}">${htmlEscape(item.title)}</a>`).join('\n')}
        </div>
      </section>`;

  return pageShell(template, {
    title: `${cluster.title} - статьи GrowerHub`,
    description: cluster.description,
    canonical,
    jsonLd: [
      collectionLd(cluster.title, cluster.description, canonical, articles),
      breadcrumbLd([
        { name: 'GrowerHub', url: SITE_URL },
        { name: 'Статьи', url: `${SITE_URL}/articles` },
        { name: cluster.title, url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderHomePage = (template, assets, articles) => pageShell(template, {
  title: 'GrowerHub - уход за растениями и умный полив',
  description: 'GrowerHub помогает структурировать уход за растениями: полив, датчики, журнал, Zigbee и локальные интеграции.',
  canonical: SITE_URL,
  jsonLd: [{
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'GrowerHub',
    url: SITE_URL,
  }],
}, `
      <h1>GrowerHub</h1>
      <p>Практичный подход к уходу за растениями: понятные данные, аккуратная автоматизация и материалы без лишних обещаний.</p>
      <section class="cluster-block">
        <h2>Новые материалы</h2>
        <div class="articles-list">
${articles.slice(0, 6).map(renderArticleCard).join('\n')}
        </div>
      </section>`, assets);

const renderAboutPage = (template, assets, aboutContent) => {
  const canonical = `${SITE_URL}/about`;
  const description = aboutContent.intro;
  const contacts = aboutContent.contacts || {};
  const mainHtml = `
      <h1>${htmlEscape(aboutContent.title)}</h1>
      <p>${htmlEscape(aboutContent.intro)}</p>
      <section class="cluster-block">
        <h2>Миссия</h2>
        <p>${htmlEscape(aboutContent.mission)}</p>
      </section>
      <section class="cluster-block">
        <h2>Что объединяет GrowerHub</h2>
        <p>${htmlEscape(aboutContent.value)}</p>
      </section>
      <section class="cluster-block">
        <h2>Контакты</h2>
        <ul>
          <li><strong>Email:</strong> <a href="mailto:${htmlEscape(contacts.email)}">${htmlEscape(contacts.email)}</a></li>
          <li><strong>Сайт:</strong> <a href="${htmlEscape(contacts.site)}">${htmlEscape(contacts.site)}</a></li>
          <li><strong>Телеграм:</strong> <a href="${htmlEscape(contacts.telegram)}">${htmlEscape(contacts.telegram)}</a></li>
        </ul>
      </section>`;

  return pageShell(template, {
    title: 'О проекте GrowerHub - контроль полива и микроклимата',
    description,
    canonical,
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'AboutPage',
        name: aboutContent.title,
        description,
        url: canonical,
        isPartOf: {
          '@type': 'WebSite',
          name: 'GrowerHub',
          url: SITE_URL,
        },
        mainEntity: {
          '@type': 'Organization',
          name: 'GrowerHub',
          url: SITE_URL,
          email: contacts.email,
          sameAs: [contacts.telegram].filter(Boolean),
        },
      },
      breadcrumbLd([
        { name: 'GrowerHub', url: SITE_URL },
        { name: 'О проекте', url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderAppNoIndexPage = (template, assets, route) => appShell(template, {
  title: route.title,
  description: route.description,
  canonical: `${SITE_URL}${route.path}`,
}, assets);

const render404 = (template, assets) => pageShell(template, {
  title: 'Страница не найдена - GrowerHub',
  description: 'Страница GrowerHub не найдена. Вернитесь к статьям или на главную страницу.',
  canonical: `${SITE_URL}/404`,
  jsonLd: [breadcrumbLd([
    { name: 'GrowerHub', url: SITE_URL },
    { name: 'Страница не найдена', url: `${SITE_URL}/404` },
  ])],
}, `
      <h1>Страница не найдена</h1>
      <p>Проверьте адрес или перейдите к материалам GrowerHub.</p>
      <p><a class="hero-cta" href="/articles">К статьям</a></p>`, assets);

const maxDate = (dates) => dates.filter(Boolean).sort().at(-1) || '2026-06-30';

const writeSitemap = (articles, articlesByCluster) => {
  const entries = [
    { loc: SITE_URL, lastmod: '2026-06-30' },
    { loc: `${SITE_URL}/about`, lastmod: STATIC_LASTMOD },
    { loc: `${SITE_URL}/articles`, lastmod: '2026-06-30' },
    ...articleClusters.map((cluster) => ({
      loc: `${SITE_URL}/articles/clusters/${cluster.slug}`,
      lastmod: maxDate((articlesByCluster.get(cluster.slug) || []).map((article) => article.updated_at || article.created_at)),
    })),
    ...articles.map((article) => ({
      loc: `${SITE_URL}/articles/${article.slug}`,
      lastmod: article.updated_at || article.created_at,
    })),
  ];

  const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries.map((entry) => `  <url>\n    <loc>${entry.loc}</loc>\n    <lastmod>${entry.lastmod}</lastmod>\n  </url>`).join('\n')}\n</urlset>\n`;
  writeText(path.join(DIST_DIR, 'sitemap.xml'), xml);
};

const writeRobots = () => {
  writeText(path.join(DIST_DIR, 'robots.txt'), `User-agent: *\nAllow: /\nDisallow: /app/\n\nSitemap: ${SITE_URL}/sitemap.xml\n`);
};

const main = () => {
  const template = readTemplate();
  const assets = extractHeadAssets(template);
  const articles = readArticles();
  const aboutContent = readJson('about.json');
  const articlesBySlug = new Map(articles.map((article) => [article.slug, article]));
  const clustersBySlug = new Map(articleClusters.map((cluster) => [cluster.slug, cluster]));
  const articlesByCluster = new Map(articleClusters.map((cluster) => [
    cluster.slug,
    articles.filter((article) => article.cluster === cluster.slug),
  ]));

  writeText(path.join(DIST_DIR, 'index.html'), renderHomePage(template, assets, articles));
  writeText(path.join(DIST_DIR, 'about', 'index.html'), renderAboutPage(template, assets, aboutContent));
  writeText(path.join(DIST_DIR, 'articles', 'index.html'), renderArticlesIndex(template, assets, articlesByCluster));
  writeText(path.join(DIST_DIR, '404.html'), render404(template, assets));

  for (const route of APP_NO_INDEX_ROUTES) {
    writeText(
      path.join(DIST_DIR, ...route.path.split('/').filter(Boolean), 'index.html'),
      renderAppNoIndexPage(template, assets, route),
    );
  }

  for (const article of articles) {
    writeText(
      path.join(DIST_DIR, 'articles', article.slug, 'index.html'),
      renderArticlePage(template, assets, article, articlesBySlug, clustersBySlug),
    );
  }

  for (const cluster of articleClusters) {
    writeText(
      path.join(DIST_DIR, 'articles', 'clusters', cluster.slug, 'index.html'),
      renderClusterPage(
        template,
        assets,
        cluster,
        articlesByCluster.get(cluster.slug) || [],
        articleClusters.filter((item) => item.slug !== cluster.slug),
      ),
    );
  }

  writeSitemap(articles, articlesByCluster);
  writeRobots();
  console.log(`Generated static pages: ${articles.length} articles, ${articleClusters.length} clusters`);
};

main();
