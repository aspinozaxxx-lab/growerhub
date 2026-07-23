import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import matter from 'gray-matter';
import { marked } from 'marked';
import { articleClusters } from '../src/content/articleClusters.js';
import {
  DEFAULT_OG_IMAGE,
  PLATFORM_START_PATH,
  SELF_SERVICE_PUBLIC_ENABLED,
  SITE_NAME,
  SITE_URL,
  TELEGRAM_CHANNEL_URL,
  TELEGRAM_DIRECT_URL,
  canonicalizePublicLinksInHtml,
  toCanonicalPath,
  toCanonicalUrl,
} from '../src/domain/siteConfig.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DIST_DIR = path.join(ROOT, 'dist');
const PUBLIC_DIR = path.join(ROOT, 'public');
const ARTICLES_DIR = path.join(ROOT, 'content', 'articles');
const PAGES_DIR = path.join(ROOT, 'content', 'pages');
const EQUIPMENT_PATH = path.join(ROOT, 'content', 'equipment', 'catalog.json');
const TEMPLATE_PATH = path.join(DIST_DIR, 'index.html');
const HOME_URL = toCanonicalUrl('/');
const APP_NO_INDEX_ROUTES = [
  { path: '/app/', title: 'GrowerHub - приложение', description: 'Личный кабинет GrowerHub для контроля растений, устройств, датчиков и полива.' },
  { path: '/app/login/', title: 'Вход в GrowerHub', description: 'Страница входа в личный кабинет GrowerHub.' },
  { path: '/app/onboarding/', title: 'Первое подключение - GrowerHub', description: 'Приватный мастер подключения Zigbee2MQTT к GrowerHub.' },
  { path: '/app/connections/', title: 'Подключения - GrowerHub', description: 'Приватный раздел координаторов GrowerHub.' },
  { path: '/app/zones/', title: 'Зоны - GrowerHub', description: 'Приватный раздел зон GrowerHub.' },
  { path: '/app/automations/', title: 'Автоматизации - GrowerHub', description: 'Приватный раздел автоматизаций GrowerHub.' },
  { path: '/app/plants/', title: 'Растения - GrowerHub', description: 'Приватный раздел GrowerHub со списком растений и журналом ухода.' },
  { path: '/app/devices/', title: 'Устройства - GrowerHub', description: 'Приватный раздел GrowerHub для просмотра устройств и датчиков.' },
  { path: '/app/profile/', title: 'Профиль - GrowerHub', description: 'Приватный раздел GrowerHub с настройками пользователя.' },
  { path: '/app/admin/', title: 'Администрирование - GrowerHub', description: 'Закрытый административный раздел GrowerHub.' },
  { path: '/app/admin/dashboard/', title: 'Дашборд фермы - GrowerHub', description: 'Закрытый дашборд фермы GrowerHub.' },
  { path: '/app/admin/product-analytics/', title: 'Продуктовая воронка - GrowerHub', description: 'Закрытая агрегированная продуктовая аналитика GrowerHub.' },
  { path: '/app/admin/users/', title: 'Пользователи - GrowerHub', description: 'Закрытый раздел управления пользователями.' },
  { path: '/app/admin/devices/', title: 'Устройства администрирования - GrowerHub', description: 'Закрытый раздел управления устройствами.' },
  { path: '/app/admin/plants/', title: 'Растения администрирования - GrowerHub', description: 'Закрытый раздел управления растениями.' },
  { path: '/app/admin/mqtt/', title: 'MQTT - GrowerHub', description: 'Закрытый раздел просмотра сообщений MQTT.' },
  { path: '/app/admin/zigbee/', title: 'Zigbee - GrowerHub', description: 'Закрытый раздел управления Zigbee-устройствами.' },
  { path: '/app/admin/automation/', title: 'Автоматизация - GrowerHub', description: 'Закрытый раздел настройки автоматизации.' },
  { path: '/app/admin/manual-watering/', title: 'Ручной полив - GrowerHub', description: 'Закрытый раздел ручного управления поливом.' },
];

const htmlEscape = (value = '') => String(value)
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
  return String(value).split(',').map((item) => item.trim()).filter(Boolean);
};

const writeText = (targetPath, content) => {
  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  fs.writeFileSync(targetPath, content, 'utf8');
};

const readTemplate = () => {
  if (!fs.existsSync(TEMPLATE_PATH)) {
    throw new Error(`Missing Vite template at ${TEMPLATE_PATH}`);
  }
  return stripBom(fs.readFileSync(TEMPLATE_PATH, 'utf8'));
};

const readJson = (name) => JSON.parse(
  stripBom(fs.readFileSync(path.join(PAGES_DIR, name), 'utf8')),
);

const readEquipmentCatalog = () => JSON.parse(stripBom(fs.readFileSync(EQUIPMENT_PATH, 'utf8')));

const readArticles = () => fs.readdirSync(ARTICLES_DIR)
  .filter((name) => name.endsWith('.md'))
  .sort()
  .map((name) => {
    const raw = stripBom(fs.readFileSync(path.join(ARTICLES_DIR, name), 'utf8'));
    const parsed = matter(raw);
    const data = parsed.data || {};
    const body = parsed.content.trim();

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
      body,
      bodyHtml: canonicalizePublicLinksInHtml(marked.parse(body)),
    };
  })
  .sort((a, b) => new Date(b.updated_at) - new Date(a.updated_at));

const extractHeadAssets = (template) => {
  const head = template.match(/<head>([\s\S]*?)<\/head>/)?.[1] || '';
  return head
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => (
      line.includes('/assets/')
      || line.includes('rel="modulepreload"')
      || line.includes('rel="stylesheet"')
    ))
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
  canonical = null,
  type = 'website',
  image = DEFAULT_OG_IMAGE,
  robots = 'index,follow',
  jsonLd = [],
  assets = '',
}) => {
  const imageUrl = image.startsWith('http') ? image : `${SITE_URL}${image}`;
  const jsonBlocks = jsonLd
    .filter(Boolean)
    .map((item) => `    <script type="application/ld+json">${JSON.stringify(item)}</script>`)
    .join('\n');
  const canonicalTags = canonical ? [
    `    <link rel="canonical" href="${htmlEscape(canonical)}" />`,
    `    <meta property="og:url" content="${htmlEscape(canonical)}" />`,
  ] : [];
  const imageDimensions = image === DEFAULT_OG_IMAGE ? [
    '    <meta property="og:image:width" content="1200" />',
    '    <meta property="og:image:height" content="630" />',
  ] : [];

  return [
    '    <meta charset="UTF-8" />',
    '    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />',
    '    <meta name="viewport" content="width=device-width, initial-scale=1.0" />',
    `    <title>${htmlEscape(title)}</title>`,
    `    <meta name="description" content="${htmlEscape(description)}" />`,
    `    <meta name="robots" content="${htmlEscape(robots)}" />`,
    ...canonicalTags,
    `    <meta property="og:site_name" content="${SITE_NAME}" />`,
    '    <meta property="og:locale" content="ru_RU" />',
    `    <meta property="og:title" content="${htmlEscape(title)}" />`,
    `    <meta property="og:description" content="${htmlEscape(description)}" />`,
    `    <meta property="og:type" content="${htmlEscape(type)}" />`,
    `    <meta property="og:image" content="${htmlEscape(imageUrl)}" />`,
    ...imageDimensions,
    '    <meta name="twitter:card" content="summary_large_image" />',
    `    <meta name="twitter:title" content="${htmlEscape(title)}" />`,
    `    <meta name="twitter:description" content="${htmlEscape(description)}" />`,
    `    <meta name="twitter:image" content="${htmlEscape(imageUrl)}" />`,
    assets,
    jsonBlocks,
  ].filter(Boolean).join('\n');
};

const telegramLink = (placement, label = 'Помощь в Telegram', className = 'secondary-link') => (
  `<a class="${className}" href="${TELEGRAM_DIRECT_URL}" target="_blank" rel="noreferrer" data-telegram-placement="${htmlEscape(placement)}">${htmlEscape(label)}</a>`
);

const platformLink = (placement, label = null, className = 'hero-cta') => {
  const href = SELF_SERVICE_PUBLIC_ENABLED ? PLATFORM_START_PATH : '/kak-nachat/';
  const resolvedLabel = label || (SELF_SERVICE_PUBLIC_ENABLED ? 'Начать бесплатно' : 'Как начать');
  return `<a class="${className}" href="${href}" data-platform-placement="${htmlEscape(placement)}">${htmlEscape(resolvedLabel)}</a>`;
};

const leadCta = (placement, title = 'Начните с первого устройства', text = 'Войдите, подключите Zigbee2MQTT и соберите первую зону самостоятельно. Бесплатно в открытой бете, без карты.') => `
      <section class="lead-cta">
        <div>
          <h2>${htmlEscape(title)}</h2>
          <p>${htmlEscape(text)}</p>
        </div>
        <div class="cta-row">
          ${platformLink(placement)}
          ${telegramLink(`${placement}_help`)}
        </div>
      </section>`;

const staticLayout = (mainHtml) => `
    <div class="app-shell">
      <header class="app-header">
        <div class="brand">
          <a href="/" class="brand-link">GrowerHub</a>
          <span class="brand-tagline">Управление фермой в одном кабинете</span>
        </div>
        <button class="menu-toggle" type="button" aria-label="Переключить меню">≡</button>
        <nav class="nav-links">
          <a class="nav-link" href="/">Главная</a>
          <a class="nav-link" href="/kak-nachat/">Как начать</a>
          <a class="nav-link" href="/oborudovanie/">Оборудование</a>
          <a class="nav-link" href="/articles/">Статьи</a>
          <a class="nav-link app-link" href="/app/">Вход</a>
          ${platformLink('header', null, 'nav-link contact-link')}
          ${telegramLink('header_help', 'Помощь', 'nav-link')}
        </nav>
      </header>
      <main class="app-main">
        <div class="section static-page">
${mainHtml}
        </div>
      </main>
      <footer class="app-footer">
        <p>© ${new Date().getFullYear()} GrowerHub. Все права защищены.</p>
        <div class="footer-links"><a href="/about/">О проекте</a><a href="/privacy/">Конфиденциальность</a><a href="/terms/">Условия</a><a href="${TELEGRAM_CHANNEL_URL}" target="_blank" rel="noreferrer">Telegram-канал</a></div>
      </footer>
    </div>
  `;

const pageShell = (template, meta, mainHtml, assets) => replaceRoot(
  replaceHead(template, makeMetaHead({ ...meta, assets })),
  staticLayout(mainHtml),
);

const appShell = (template, meta, assets) => replaceHead(
  template,
  makeMetaHead({ ...meta, robots: 'noindex,nofollow', assets }),
);

const formatDate = (date) => (date ? new Date(date).toLocaleDateString('ru-RU') : '');

const renderArticleCard = (article) => `
          <article class="article-card">
            <div class="article-meta">Обновлено ${htmlEscape(formatDate(article.updated_at))}</div>
            <a href="/articles/${htmlEscape(article.slug)}/">${htmlEscape(article.title)}</a>
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

const organizationLd = {
  '@type': 'Organization',
  name: SITE_NAME,
  url: HOME_URL,
};

const articleLd = (article, cluster) => ({
  '@context': 'https://schema.org',
  '@type': 'BlogPosting',
  headline: article.title,
  description: article.summary,
  datePublished: article.created_at,
  dateModified: article.updated_at,
  image: article.hero_image ? `${SITE_URL}${article.hero_image}` : `${SITE_URL}${DEFAULT_OG_IMAGE}`,
  mainEntityOfPage: toCanonicalUrl(`/articles/${article.slug}/`),
  articleSection: cluster?.title,
  keywords: article.keywords,
  author: organizationLd,
  publisher: organizationLd,
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
    url: toCanonicalUrl(`/articles/${article.slug}/`),
  })),
});

const renderArticlePage = (template, assets, article, articlesBySlug, clustersBySlug) => {
  const cluster = clustersBySlug.get(article.cluster);
  const related = article.related.map((slug) => articlesBySlug.get(slug)).filter(Boolean).slice(0, 4);
  const canonical = toCanonicalUrl(`/articles/${article.slug}/`);
  const heroInBody = article.hero_image && article.body.includes(`](${article.hero_image})`);
  const mainHtml = `
          <div class="article-meta">Обновлено ${htmlEscape(formatDate(article.updated_at))}</div>
          <h1>${htmlEscape(article.title)}</h1>
          <p class="article-lead">${htmlEscape(article.summary)}</p>
          ${cluster ? `<p><a class="secondary-link" href="/articles/clusters/${htmlEscape(cluster.slug)}/">${htmlEscape(cluster.title)}</a></p>` : ''}
          ${article.hero_image && !heroInBody ? `<img class="article-hero-image" src="${htmlEscape(article.hero_image)}" alt="${htmlEscape(article.hero_alt)}" />` : ''}
          <div class="article-body">
${article.bodyHtml}
          </div>
          ${leadCta('article_bottom', 'Подключите устройство к GrowerHub', 'Войдите, настройте Zigbee2MQTT и увидьте метрики в кабинете. Если потребуется помощь, Telegram доступен на каждом шаге.')}
          ${related.length ? `
          <section class="related-articles">
            <h2>Читайте также</h2>
            <div class="articles-list">
${related.map(renderArticleCard).join('\n')}
            </div>
          </section>` : ''}
          <div class="content-section"><a class="secondary-link" href="/articles/">Назад к статьям</a></div>`;

  return pageShell(template, {
    title: `${article.title} - GrowerHub`,
    description: article.summary,
    canonical,
    type: 'article',
    image: article.hero_image || DEFAULT_OG_IMAGE,
    jsonLd: [
      articleLd(article, cluster),
      breadcrumbLd([
        { name: 'GrowerHub', url: HOME_URL },
        { name: 'Статьи', url: toCanonicalUrl('/articles/') },
        ...(cluster ? [{ name: cluster.title, url: toCanonicalUrl(`/articles/clusters/${cluster.slug}/`) }] : []),
        { name: article.title, url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderArticlesIndex = (template, assets, articlesByCluster) => {
  const canonical = toCanonicalUrl('/articles/');
  const allArticles = [...articlesByCluster.values()].flat();
  const description = 'Практические материалы GrowerHub об автополиве, датчиках, Zigbee2MQTT, Home Assistant и автоматизации мини-фермы.';
  const mainHtml = `
          <h1>Статьи GrowerHub</h1>
          <p>${htmlEscape(description)}</p>
          <div class="cluster-list">
${articleClusters.map((cluster) => `
            <section class="cluster-block">
              <div class="cluster-block__header">
                <div><h2>${htmlEscape(cluster.title)}</h2><p>${htmlEscape(cluster.description)}</p></div>
                <a class="secondary-link" href="/articles/clusters/${htmlEscape(cluster.slug)}/">Раздел</a>
              </div>
              <div class="cluster-meta-grid">
                <div><strong>Подходит, если</strong><p>${htmlEscape(cluster.fit)}</p></div>
                <div><strong>Задачи, которые разбираем</strong><p>${htmlEscape(cluster.tasks)}</p></div>
              </div>
              <div class="articles-list">
${(articlesByCluster.get(cluster.slug) || []).map(renderArticleCard).join('\n')}
              </div>
            </section>`).join('\n')}
          </div>
          ${leadCta('articles_index_bottom')}`;

  return pageShell(template, {
    title: 'Статьи GrowerHub — датчики, полив и автоматизация',
    description,
    canonical,
    jsonLd: [
      collectionLd('Статьи GrowerHub', description, canonical, allArticles),
      breadcrumbLd([
        { name: 'GrowerHub', url: HOME_URL },
        { name: 'Статьи', url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderClusterPage = (template, assets, cluster, articles, otherClusters) => {
  const canonical = toCanonicalUrl(`/articles/clusters/${cluster.slug}/`);
  const mainHtml = `
          <div class="article-meta">Практический раздел</div>
          <h1>${htmlEscape(cluster.title)}</h1>
          <p>${htmlEscape(cluster.description)}</p>
          <div class="cluster-meta-grid">
            <div><strong>Подходит, если</strong><p>${htmlEscape(cluster.fit)}</p></div>
            <div><strong>Задачи, которые разбираем</strong><p>${htmlEscape(cluster.tasks)}</p></div>
          </div>
          <div class="keyword-list">
${cluster.keywords.map((keyword) => `            <span>${htmlEscape(keyword)}</span>`).join('\n')}
          </div>
          <section class="cluster-block">
            <h2>Статьи раздела</h2>
            <div class="articles-list">
${articles.map(renderArticleCard).join('\n')}
            </div>
          </section>
          ${leadCta('cluster_bottom')}
          <section class="cluster-block">
            <h2>Другие разделы</h2>
            <div class="cluster-nav-grid">
${otherClusters.map((item) => `              <a href="/articles/clusters/${htmlEscape(item.slug)}/">${htmlEscape(item.title)}</a>`).join('\n')}
            </div>
          </section>`;

  return pageShell(template, {
    title: `${cluster.title} - статьи GrowerHub`,
    description: cluster.description,
    canonical,
    jsonLd: [
      collectionLd(cluster.title, cluster.description, canonical, articles),
      breadcrumbLd([
        { name: 'GrowerHub', url: HOME_URL },
        { name: 'Статьи', url: toCanonicalUrl('/articles/') },
        { name: cluster.title, url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderHomePage = (template, assets, homeContent, articles, articlesBySlug) => {
  const { hero, secondary, features } = homeContent;
  const description = 'GrowerHub — платформа, в которой собран большой практический опыт автоматизации теплиц: Zigbee-устройства, зоны, история датчиков и сценарии управления.';
  const mainHtml = `
          <div class="hero">
            <div>
              <div class="badge">${htmlEscape(hero.badge)}</div>
              <h1>${htmlEscape(hero.title)}</h1>
              <p>${htmlEscape(hero.subtitle)}</p>
              <div class="cta-row">
                ${platformLink('home_hero', SELF_SERVICE_PUBLIC_ENABLED ? hero.cta : 'Как начать')}
                <a class="secondary-link" href="/kak-nachat/">Путь подключения</a>
              </div>
            </div>
            <div class="card">
              <h2>${htmlEscape(secondary.title)}</h2>
              <p>${htmlEscape(secondary.text)}</p>
              <div class="card-grid">
${secondary.points.map((point) => `                <div class="info-block"><strong>${htmlEscape(point.title)}</strong><p>${htmlEscape(point.text)}</p></div>`).join('\n')}
              </div>
            </div>
          </div>
          <section class="content-section">
            <h2>${htmlEscape(features.title)}</h2>
            <div class="card-grid">
${features.items.map((item) => `              <div class="card"><h3>${htmlEscape(item.title)}</h3><p>${htmlEscape(item.text)}</p></div>`).join('\n')}
            </div>
          </section>
          <section class="content-section early-access-note">
            <h2>Большой опыт автоматизации — в одной платформе</h2>
            <p>${htmlEscape(homeContent.early_access)}</p>
            <div class="cta-row"><a class="secondary-link" href="/oborudovanie/">Какое оборудование подойдёт</a><a class="secondary-link" href="/avtomatizatsiya-mini-fermy/">Возможности платформы</a></div>
          </section>
          <section class="content-section">
            <h2>Практические разделы</h2>
            <div class="cluster-home-grid">
${articleClusters.map((cluster) => {
    const featured = cluster.featuredArticles.map((slug) => articlesBySlug.get(slug)).filter(Boolean).slice(0, 4);
    return `              <article class="article-card">
                <a href="/articles/clusters/${htmlEscape(cluster.slug)}/">${htmlEscape(cluster.title)}</a>
                <p>${htmlEscape(cluster.description)}</p>
                <ul class="compact-link-list">${featured.map((article) => `<li><a href="/articles/${htmlEscape(article.slug)}/">${htmlEscape(article.title)}</a></li>`).join('')}</ul>
              </article>`;
  }).join('\n')}
            </div>
          </section>
          <section class="content-section">
            <div class="cluster-block__header"><div><h2>Свежие статьи</h2><p>Пошаговые материалы по Zigbee, Home Assistant, датчикам и безопасному поливу.</p></div><a class="secondary-link" href="/articles/">Все статьи</a></div>
            <div class="articles-list">
${articles.slice(0, 4).map(renderArticleCard).join('\n')}
            </div>
          </section>
          ${leadCta('home_bottom')}`;

  return pageShell(template, {
    title: 'GrowerHub — платформа управления мини-фермой',
    description,
    canonical: HOME_URL,
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        name: SITE_NAME,
        url: HOME_URL,
      },
      {
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: SITE_NAME,
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        url: HOME_URL,
        description,
        ...(SELF_SERVICE_PUBLIC_ENABLED ? {
          isAccessibleForFree: true,
          offers: { '@type': 'Offer', price: '0', priceCurrency: 'RUB' },
        } : {}),
      },
    ],
  }, mainHtml, assets);
};

const renderAboutPage = (template, assets, aboutContent) => {
  const canonical = toCanonicalUrl('/about/');
  const mainHtml = `
          <h1>${htmlEscape(aboutContent.title)}</h1>
          <p>${htmlEscape(aboutContent.intro)}</p>
          <div class="info-grid content-section">
            <div class="info-block"><h2>Задача проекта</h2><p>${htmlEscape(aboutContent.mission)}</p></div>
            <div class="info-block"><h2>Чем помогаем</h2><p>${htmlEscape(aboutContent.value)}</p></div>
          </div>
          <section class="content-section">
            <h2>Контакты</h2>
            <ul class="contact-list">
              <li><strong>Сайт:</strong> <a href="${htmlEscape(aboutContent.contacts.site)}">${htmlEscape(aboutContent.contacts.site)}</a></li>
              <li><strong>Telegram:</strong> <a href="${TELEGRAM_CHANNEL_URL}" target="_blank" rel="noreferrer">канал GrowerHub</a></li>
            </ul>
          </section>
          ${leadCta('about_bottom')}`;

  return pageShell(template, {
    title: 'О проекте GrowerHub — контроль полива и микроклимата',
    description: aboutContent.intro,
    canonical,
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'AboutPage',
        name: aboutContent.title,
        description: aboutContent.intro,
        url: canonical,
        isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: HOME_URL },
        mainEntity: { ...organizationLd, sameAs: [TELEGRAM_CHANNEL_URL] },
      },
      breadcrumbLd([
        { name: 'GrowerHub', url: HOME_URL },
        { name: 'О проекте', url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderMiniFarmDemos = (screens) => `
          <section class="content-section" id="demo-ekrany">
            <h2>Интерфейс на синтетических данных</h2>
            <p>Все названия и значения вымышлены. На экранах нет реальных адресов, IEEE, логинов или данных доступа.</p>
            <div class="demo-grid demo-grid--four">
              <figure class="demo-card">
                <img class="product-screenshot" src="/screenshots/zones.png" alt="Обзор двух зон GrowerHub на синтетических данных" width="1010" height="520" loading="eager">
                <figcaption><strong>${htmlEscape(screens[0].title)}</strong><span>${htmlEscape(screens[0].text)}</span></figcaption>
              </figure>
              <figure class="demo-card">
                <img class="product-screenshot" src="/screenshots/history.png" alt="История температуры и влажности GrowerHub на синтетических данных" width="1010" height="520" loading="lazy">
                <figcaption><strong>${htmlEscape(screens[1].title)}</strong><span>${htmlEscape(screens[1].text)}</span></figcaption>
              </figure>
              <figure class="demo-card">
                <img class="product-screenshot" src="/screenshots/connection.png" alt="Подключение Zigbee-координатора GrowerHub на синтетических данных" width="1010" height="520" loading="lazy">
                <figcaption><strong>${htmlEscape(screens[2].title)}</strong><span>${htmlEscape(screens[2].text)}</span></figcaption>
              </figure>
              <figure class="demo-card">
                <img class="product-screenshot" src="/screenshots/automation.png" alt="Автоматизации GrowerHub на синтетических данных" width="1010" height="520" loading="lazy">
                <figcaption><strong>${htmlEscape(screens[3].title)}</strong><span>${htmlEscape(screens[3].text)}</span></figcaption>
              </figure>
            </div>
          </section>`;

const renderMiniFarmPage = (template, assets, data) => {
  const canonical = toCanonicalUrl('/avtomatizatsiya-mini-fermy/');
  const mainHtml = `
          <section class="landing-hero">
            <div><div class="badge">${htmlEscape(data.hero.eyebrow)}</div><h1>${htmlEscape(data.title)}</h1><p>${htmlEscape(data.hero.text)}</p><div class="cta-row">${platformLink('mini_farm_hero', SELF_SERVICE_PUBLIC_ENABLED ? data.hero.cta : 'Как начать')}<a class="secondary-link" href="/oborudovanie/">Подобрать оборудование</a></div></div>
            <aside class="landing-summary"><strong>Ранний доступ открыт</strong><p>${htmlEscape(data.early_access)}</p><p>Начните с оборудования и зон; растения и дополнительные настройки можно добавить позже.</p></aside>
          </section>
          <section class="content-section"><h2>Что можно сделать</h2><div class="card-grid">${data.tasks.map((item) => `<article class="card"><h3>${htmlEscape(item.title)}</h3><p>${htmlEscape(item.text)}</p></article>`).join('')}</div></section>
          <section class="content-section split-section">
            <div><h2>Возможности платформы</h2><ul class="check-list">${data.capabilities.map((item) => `<li>${htmlEscape(item)}</li>`).join('')}</ul></div>
            <div class="info-block"><h2>${htmlEscape(data.compatibility.title)}</h2><p>${htmlEscape(data.compatibility.text)}</p><p class="source-links"><a href="https://www.zigbee2mqtt.io/supported-devices/" target="_blank" rel="noreferrer">Каталог Zigbee2MQTT</a> · <a href="/oborudovanie/">Оборудование для старта</a></p></div>
          </section>
          ${renderMiniFarmDemos(data.screens)}
          <section class="content-section"><h2>Путь от входа до дашборда</h2><ol class="steps-list">${data.stages.map((stage) => `<li><strong>${htmlEscape(stage.title)}</strong><span>${htmlEscape(stage.text)}</span></li>`).join('')}</ol></section>
          <section class="content-section split-section">
            <div><h2>Что важно знать</h2><ul class="check-list limitations-list">${data.limitations.map((item) => `<li>${htmlEscape(item)}</li>`).join('')}</ul></div>
            <div class="info-block"><h2>Мы рядом, если понадобится помощь</h2><p>Напишите нам в Telegram — поможем подключить оборудование, разобраться с функциями и настроить GrowerHub под вашу ферму.</p>${telegramLink('mini_farm_help')}</div>
          </section>
          ${leadCta('mini_farm_bottom', 'Подключите первое устройство', 'Начните самостоятельно с координатора и датчика. Зоны и автоматизации можно добавлять постепенно.')}`;

  return pageShell(template, {
    title: data.title,
    description: data.description,
    canonical,
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: SITE_NAME,
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        description: data.description,
        url: canonical,
        ...(SELF_SERVICE_PUBLIC_ENABLED ? {
          isAccessibleForFree: true,
          offers: { '@type': 'Offer', price: '0', priceCurrency: 'RUB' },
        } : {}),
      },
      breadcrumbLd([
        { name: 'GrowerHub', url: HOME_URL },
        { name: 'Автоматизация мини-фермы', url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderGettingStartedPage = (template, assets, platformContent) => {
  const { start, minimum, early_access_text: earlyAccessText } = platformContent;
  const canonical = toCanonicalUrl('/kak-nachat/');
  const mainHtml = `
          <section class="landing-hero">
            <div><div class="badge">Самостоятельный запуск</div><h1>${htmlEscape(start.title)}</h1><p>${htmlEscape(start.intro)}</p>${leadCta('getting_started_hero')}</div>
            <aside class="landing-summary"><strong>Ранний доступ открыт</strong><p>${htmlEscape(earlyAccessText)}</p></aside>
          </section>
          <section class="content-section"><h2>Семь коротких шагов</h2><ol class="steps-list">${start.steps.map((step) => `<li><strong>${htmlEscape(step.title)}</strong><span>${htmlEscape(step.text)}</span></li>`).join('')}</ol></section>
          <section class="content-section split-section">
            <div><h2>${htmlEscape(minimum.title)}</h2><div class="info-grid"><div class="info-block"><h3>Только мониторинг</h3><p>${htmlEscape(minimum.monitoring)}</p></div><div class="info-block"><h3>Управление</h3><p>${htmlEscape(minimum.control)}</p></div></div></div>
            <div class="info-block"><h2>Уже есть Home Assistant?</h2><p>${htmlEscape(minimum.existing)}</p><a class="secondary-link" href="/oborudovanie/zigbee-koordinator/">Проверить оборудование</a></div>
          </section>
          <section class="content-section"><h2>Поможем с подключением и настройкой</h2><p>Если что-то не подключается или хочется быстрее разобраться с функцией, напишите нам в Telegram. Команда GrowerHub поможет на любом этапе.</p>${telegramLink('getting_started_help')}</section>
          ${leadCta('getting_started_bottom')}`;

  return pageShell(template, {
    title: `${start.title} — подключение Zigbee2MQTT`,
    description: start.description,
    canonical,
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'HowTo',
        name: start.title,
        description: start.description,
        url: canonical,
        publisher: organizationLd,
        step: start.steps.map((step, index) => ({
          '@type': 'HowToStep',
          position: index + 1,
          name: step.title,
          text: step.text,
        })),
      },
      breadcrumbLd([{ name: SITE_NAME, url: HOME_URL }, { name: 'Как начать', url: canonical }]),
    ],
  }, mainHtml, assets);
};

const renderEquipmentIndexPage = (template, assets, equipment, platformContent) => {
  const canonical = toCanonicalUrl('/oborudovanie/');
  const description = 'Что нужно для GrowerHub: Zigbee-координатор, один датчик температуры и влажности, а для управления — Zigbee-розетка.';
  const categories = Object.values(equipment.categories);
  const mainHtml = `
          <div class="badge">Без обязательных комплектов</div>
          <h1>Оборудование для GrowerHub</h1>
          <p class="article-lead">${htmlEscape(description)}</p>
          <section class="content-section start-kit"><h2>Минимальный старт</h2><div class="card-grid"><article class="card"><h3>Для мониторинга</h3><p>${htmlEscape(platformContent.minimum.monitoring)}</p></article><article class="card"><h3>Для управления</h3><p>${htmlEscape(platformContent.minimum.control)}</p></article><article class="card"><h3>Если всё уже работает</h3><p>${htmlEscape(platformContent.minimum.existing)}</p></article></div></section>
          <section class="content-section"><h2>Выберите раздел</h2><div class="card-grid">
${categories.map((category) => `            <article class="card"><h3>${htmlEscape(category.title)}</h3><p>${htmlEscape(category.intro)}</p><a class="secondary-link" href="/oborudovanie/${htmlEscape(category.slug)}/">Посмотреть варианты</a></article>`).join('\n')}
            <article class="card"><h3>${htmlEscape(equipment.pump.title)}</h3><p>${htmlEscape(equipment.pump.summary)}</p><a class="secondary-link" href="/oborudovanie/nasos-dlya-poliva/">О насосе GrowerHub</a></article>
          </div></section>
          <section class="content-section info-block"><h2>Почему не любой Wi‑Fi-датчик</h2><p>${htmlEscape(equipment.wifi_note)}</p></section>
          ${leadCta('equipment_index_bottom')}`;

  return pageShell(template, {
    title: 'Оборудование для GrowerHub — мягкие рекомендации',
    description,
    canonical,
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: 'Оборудование для GrowerHub',
        description,
        url: canonical,
        isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: HOME_URL },
        mainEntity: {
          '@type': 'ItemList',
          itemListElement: categories.map((category, index) => ({
            '@type': 'ListItem',
            position: index + 1,
            name: category.title,
            url: toCanonicalUrl(`/oborudovanie/${category.slug}/`),
          })),
        },
      },
      breadcrumbLd([{ name: SITE_NAME, url: HOME_URL }, { name: 'Оборудование', url: canonical }]),
    ],
  }, mainHtml, assets);
};

const renderEquipmentCategoryPage = (template, assets, equipment, categoryKey) => {
  const category = equipment.categories[categoryKey];
  const canonical = toCanonicalUrl(`/oborudovanie/${category.slug}/`);
  const mainHtml = `
          <a class="secondary-link" href="/oborudovanie/">← Всё оборудование</a>
          <div class="badge">Проверено ${htmlEscape(formatDate(equipment.checked_at))}</div>
          <h1>${htmlEscape(category.title)}</h1>
          <p class="article-lead">${htmlEscape(category.intro)}</p>
          <p class="equipment-disclaimer">${htmlEscape(equipment.purchase_note)}</p>
          <div class="equipment-list content-section">
${category.items.map((item) => `            <article class="equipment-card"><div><span class="status-chip">${htmlEscape(item.status)}</span><h2>${htmlEscape(item.model)}</h2><h3>${htmlEscape(item.name)}</h3><p>${htmlEscape(item.summary)}</p></div><ul class="check-list">${item.notes.map((note) => `<li>${htmlEscape(note)}</li>`).join('')}</ul><div class="cta-row"><a class="secondary-link" href="${htmlEscape(item.official_url)}" target="_blank" rel="noreferrer">Совместимость Zigbee2MQTT</a><a class="secondary-link" href="${htmlEscape(item.shop_search_url)}" target="_blank" rel="nofollow noreferrer">Поиск модели на Ozon</a></div></article>`).join('\n')}
          </div>
          ${categoryKey === 'sensors' ? `<section class="content-section info-block"><h2>Wi‑Fi-датчики</h2><p>${htmlEscape(equipment.wifi_note)}</p></section>` : ''}
          ${leadCta(`equipment_${category.slug}_bottom`)}`;

  return pageShell(template, {
    title: `${category.title} — GrowerHub`,
    description: category.description,
    canonical,
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: category.title,
        description: category.description,
        url: canonical,
        isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: HOME_URL },
        mainEntity: {
          '@type': 'ItemList',
          itemListElement: category.items.map((item, index) => ({
            '@type': 'ListItem',
            position: index + 1,
            name: item.model,
            url: item.official_url,
          })),
        },
      },
      breadcrumbLd([
        { name: SITE_NAME, url: HOME_URL },
        { name: 'Оборудование', url: toCanonicalUrl('/oborudovanie/') },
        { name: category.title, url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderPumpEarlyAccessPage = (template, assets, equipment) => {
  const pump = equipment.pump;
  const canonical = toCanonicalUrl('/oborudovanie/nasos-dlya-poliva/');
  const mainHtml = `
          <div class="badge">${htmlEscape(pump.status)}</div><h1>${htmlEscape(pump.title)}</h1><p class="article-lead">${htmlEscape(pump.summary)}</p>
          <section class="content-section split-section"><div class="info-block"><h2>Два варианта связи</h2><p>Проектируем два режима: Zigbee для общей сети устройств и Wi‑Fi с прямым MQTT GrowerHub. Оба варианта работают с единым кабинетом платформы.</p></div><div class="info-block"><h2>Текущий этап</h2><p>Прототип проходит испытания. Если хотите присоединиться к первым пользователям, напишите нам — обсудим оборудование и подходящий сценарий.</p></div></section>
          <section class="content-section"><h2>Что мы проверяем</h2><ul class="check-list limitations-list">${pump.limitations.map((item) => `<li>${htmlEscape(item)}</li>`).join('')}</ul></section>
          <section class="lead-cta"><div><h2>Стать одним из первых пользователей</h2><p>Расскажите, где планируете использовать насос. Мы ответим на вопросы и подскажем, как подготовиться к первым испытаниям.</p></div>${telegramLink('pump_early_access', 'Написать в Telegram', 'hero-cta')}</section>
          ${leadCta('pump_platform_bottom', 'Начать с готового оборудования', 'Для мониторинга GrowerHub насос не нужен: достаточно координатора и одного Zigbee-датчика.')}`;

  return pageShell(template, {
    title: `${pump.title} — GrowerHub`,
    description: pump.description,
    canonical,
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        name: pump.title,
        description: pump.description,
        url: canonical,
        isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: HOME_URL },
      },
      breadcrumbLd([
        { name: SITE_NAME, url: HOME_URL },
        { name: 'Оборудование', url: toCanonicalUrl('/oborudovanie/') },
        { name: pump.title, url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderLegalPage = (template, assets, legal, type) => {
  const isPrivacy = type === 'privacy';
  const routePath = isPrivacy ? '/privacy/' : '/terms/';
  const title = isPrivacy ? legal.privacy_title : legal.terms_title;
  const ready = legal.reviewed && legal.operator_name && legal.operator_contact;
  const mainHtml = ready ? `
          <h1>${htmlEscape(title)}</h1><p>Оператор: ${htmlEscape(legal.operator_name)}. Контакт: ${htmlEscape(legal.operator_contact)}.</p>
          ${isPrivacy
    ? '<h2>Какие данные обрабатываются</h2><p>Адрес электронной почты и идентификатор выбранного способа входа, технические данные подключений и устройств, события безопасности, настройки зон и автоматизаций.</p><h2>Для чего</h2><p>Для входа, работы платформы, защиты пользовательского пространства, диагностики и поддержки по обращению пользователя.</p><h2>Секреты подключения</h2><p>Одноразовый MQTT-пароль показывается при создании или ротации и не хранится в базе GrowerHub в открытом виде. Локальные данные доступа Home Assistant вводятся только в браузере для скачиваемого файла.</p><h2>Обращения</h2><p>По вопросам данных и удаления аккаунта используйте доменный контакт оператора.</p>'
    : '<h2>Ранний доступ</h2><p>GrowerHub доступен бесплатно и без карты. Основные функции уже работают, а каталог устройств и сценариев постоянно расширяется. О важных изменениях сообщим заранее.</p><h2>Подключение оборудования</h2><p>Для оборудования с водой и сетевым питанием соблюдайте электробезопасность, проверяйте нагрузку и предусмотрите физическое аварийное отключение.</p><h2>Поддержка</h2><p>Если понадобится помощь, напишите нам в Telegram — подскажем с подключением, устройствами и настройкой функций.</p>'}` : `
          <div class="badge">Информация обновляется</div><h1>${htmlEscape(title)}</h1><p>Мы обновляем эту страницу. Если у вас есть вопрос о GrowerHub или работе с данными, напишите команде в Telegram.</p>`;

  return pageShell(template, {
    title,
    description: `${title} для пользователей платформы GrowerHub.`,
    canonical: toCanonicalUrl(routePath),
    robots: 'noindex,follow',
  }, mainHtml, assets);
};

const renderAppNoIndexPage = (template, assets, route) => appShell(template, {
  title: route.title,
  description: route.description,
  canonical: toCanonicalUrl(route.path),
}, assets);

const render404 = (template, assets) => pageShell(template, {
  title: 'Страница не найдена - GrowerHub',
  description: 'Страница GrowerHub не найдена. Вернитесь на главную страницу или к практическим материалам.',
  canonical: null,
  robots: 'noindex,nofollow',
}, `
          <div class="article-meta">Ошибка 404</div>
          <h1>Страница не найдена</h1>
          <p>Страница GrowerHub не найдена. Вернитесь на главную страницу или к практическим материалам.</p>
          <div class="cta-row"><a class="hero-cta" href="/">На главную</a><a class="secondary-link" href="/articles/">К статьям</a></div>`, assets);

const maxDate = (dates) => dates.filter(Boolean).sort().at(-1);

const writeSitemap = (
  articles,
  articlesByCluster,
  homeContent,
  aboutContent,
  miniFarmContent,
  platformContent,
  equipment,
) => {
  const latestArticleDate = maxDate(articles.map((article) => article.updated_at));
  const entries = [
    { loc: HOME_URL, lastmod: homeContent.updated_at },
    { loc: toCanonicalUrl('/about/'), lastmod: aboutContent.updated_at },
    { loc: toCanonicalUrl('/articles/'), lastmod: latestArticleDate },
    { loc: toCanonicalUrl('/avtomatizatsiya-mini-fermy/'), lastmod: miniFarmContent.updated_at },
    { loc: toCanonicalUrl('/kak-nachat/'), lastmod: platformContent.updated_at },
    { loc: toCanonicalUrl('/oborudovanie/'), lastmod: equipment.updated_at },
    { loc: toCanonicalUrl('/oborudovanie/zigbee-koordinator/'), lastmod: equipment.updated_at },
    { loc: toCanonicalUrl('/oborudovanie/datchiki/'), lastmod: equipment.updated_at },
    { loc: toCanonicalUrl('/oborudovanie/zigbee-rozetki/'), lastmod: equipment.updated_at },
    { loc: toCanonicalUrl('/oborudovanie/nasos-dlya-poliva/'), lastmod: equipment.updated_at },
    ...articleClusters.map((cluster) => ({
      loc: toCanonicalUrl(`/articles/clusters/${cluster.slug}/`),
      lastmod: maxDate((articlesByCluster.get(cluster.slug) || []).map((article) => article.updated_at)),
    })),
    ...articles.map((article) => ({
      loc: toCanonicalUrl(`/articles/${article.slug}/`),
      lastmod: article.updated_at,
    })),
  ];

  if (entries.length !== 69) {
    throw new Error(`Expected 69 public sitemap URLs, got ${entries.length}`);
  }

  const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries.map((entry) => `  <url>\n    <loc>${entry.loc}</loc>\n    <lastmod>${entry.lastmod}</lastmod>\n  </url>`).join('\n')}\n</urlset>\n`;
  writeText(path.join(DIST_DIR, 'sitemap.xml'), xml);
  writeText(path.join(PUBLIC_DIR, 'sitemap.xml'), xml);
};

const writeRobots = () => {
  const content = `User-agent: *\nAllow: /\nDisallow: /app\n\nSitemap: ${SITE_URL}/sitemap.xml\n`;
  writeText(path.join(DIST_DIR, 'robots.txt'), content);
  writeText(path.join(PUBLIC_DIR, 'robots.txt'), content);
};

const main = () => {
  const template = readTemplate();
  const assets = extractHeadAssets(template);
  const articles = readArticles();
  const homeContent = readJson('home.json');
  const aboutContent = readJson('about.json');
  const miniFarmContent = readJson('mini-farm.json');
  const platformContent = readJson('platform.json');
  const legalContent = readJson('legal.json');
  const equipment = readEquipmentCatalog();
  if (SELF_SERVICE_PUBLIC_ENABLED && (
    !legalContent.reviewed
    || !legalContent.operator_name
    || !legalContent.operator_contact
  )) {
    throw new Error('Self-service cannot be published before legal operator data is reviewed');
  }
  const articlesBySlug = new Map(articles.map((article) => [article.slug, article]));
  const clustersBySlug = new Map(articleClusters.map((cluster) => [cluster.slug, cluster]));
  const articlesByCluster = new Map(articleClusters.map((cluster) => [
    cluster.slug,
    articles.filter((article) => article.cluster === cluster.slug),
  ]));

  writeText(path.join(DIST_DIR, 'index.html'), renderHomePage(template, assets, homeContent, articles, articlesBySlug));
  writeText(path.join(DIST_DIR, 'about', 'index.html'), renderAboutPage(template, assets, aboutContent));
  writeText(path.join(DIST_DIR, 'articles', 'index.html'), renderArticlesIndex(template, assets, articlesByCluster));
  writeText(path.join(DIST_DIR, 'avtomatizatsiya-mini-fermy', 'index.html'), renderMiniFarmPage(template, assets, miniFarmContent));
  writeText(path.join(DIST_DIR, 'kak-nachat', 'index.html'), renderGettingStartedPage(template, assets, platformContent));
  writeText(path.join(DIST_DIR, 'oborudovanie', 'index.html'), renderEquipmentIndexPage(template, assets, equipment, platformContent));
  writeText(path.join(DIST_DIR, 'oborudovanie', 'zigbee-koordinator', 'index.html'), renderEquipmentCategoryPage(template, assets, equipment, 'coordinators'));
  writeText(path.join(DIST_DIR, 'oborudovanie', 'datchiki', 'index.html'), renderEquipmentCategoryPage(template, assets, equipment, 'sensors'));
  writeText(path.join(DIST_DIR, 'oborudovanie', 'zigbee-rozetki', 'index.html'), renderEquipmentCategoryPage(template, assets, equipment, 'sockets'));
  writeText(path.join(DIST_DIR, 'oborudovanie', 'nasos-dlya-poliva', 'index.html'), renderPumpEarlyAccessPage(template, assets, equipment));
  writeText(path.join(DIST_DIR, 'privacy', 'index.html'), renderLegalPage(template, assets, legalContent, 'privacy'));
  writeText(path.join(DIST_DIR, 'terms', 'index.html'), renderLegalPage(template, assets, legalContent, 'terms'));
  writeText(path.join(DIST_DIR, '404.html'), render404(template, assets));

  for (const route of APP_NO_INDEX_ROUTES) {
    writeText(
      path.join(DIST_DIR, ...toCanonicalPath(route.path).split('/').filter(Boolean), 'index.html'),
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

  writeSitemap(
    articles,
    articlesByCluster,
    homeContent,
    aboutContent,
    miniFarmContent,
    platformContent,
    equipment,
  );
  writeRobots();
  console.log(`Generated static pages: ${articles.length} articles, ${articleClusters.length} clusters, 69 sitemap URLs`);
};

main();
