import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import matter from 'gray-matter';
import { marked } from 'marked';
import {
  articleClusters,
  getArticleClusters,
} from '../src/content/articleClusters.js';
import {
  PUBLIC_ROUTES,
  getArticlePath,
  getClusterPath,
  getPlatformStartPath,
  getPublicPath,
} from '../src/domain/localizedRoutes.js';
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
const EN_ARTICLES_DIR = path.join(ROOT, 'content', 'en', 'articles');
const PAGES_DIR = path.join(ROOT, 'content', 'pages');
const EN_PAGES_DIR = path.join(ROOT, 'content', 'en', 'pages');
const EQUIPMENT_PATH = path.join(ROOT, 'content', 'equipment', 'catalog.json');
const EN_EQUIPMENT_PATH = path.join(ROOT, 'content', 'en', 'equipment', 'catalog.json');
const TEMPLATE_PATH = path.join(DIST_DIR, 'index.html');
const HOME_URL = toCanonicalUrl('/');
const localizedPairByCanonical = new Map();

const registerLocalizedPair = (ruPath, enPath) => {
  const pair = { ru: toCanonicalUrl(ruPath), en: toCanonicalUrl(enPath) };
  localizedPairByCanonical.set(pair.ru, pair);
  localizedPairByCanonical.set(pair.en, pair);
};

Object.values(PUBLIC_ROUTES).forEach((paths) => registerLocalizedPair(paths.ru, paths.en));
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

const readJson = (name, locale = 'ru') => JSON.parse(
  stripBom(fs.readFileSync(path.join(locale === 'en' ? EN_PAGES_DIR : PAGES_DIR, name), 'utf8')),
);

const readEquipmentCatalog = (locale = 'ru') => JSON.parse(stripBom(fs.readFileSync(
  locale === 'en' ? EN_EQUIPMENT_PATH : EQUIPMENT_PATH,
  'utf8',
)));

const readArticles = (locale = 'ru') => fs.readdirSync(locale === 'en' ? EN_ARTICLES_DIR : ARTICLES_DIR)
  .filter((name) => name.endsWith('.md'))
  .sort()
  .map((name) => {
    const raw = stripBom(fs.readFileSync(
      path.join(locale === 'en' ? EN_ARTICLES_DIR : ARTICLES_DIR, name),
      'utf8',
    ));
    const parsed = matter(raw);
    const data = parsed.data || {};
    const body = parsed.content.trim();

    return {
      id: locale === 'en' ? data.translation_of : (data.slug || path.basename(name, '.md')),
      locale,
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

const replaceHtmlLang = (template, locale) => template.replace(
  /<html\s+lang="[^"]+"/,
  `<html lang="${locale === 'en' ? 'en' : 'ru'}"`,
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
  locale: explicitLocale,
}) => {
  const locale = explicitLocale || (canonical && new URL(canonical).pathname.startsWith('/en/') ? 'en' : 'ru');
  const localizedPair = canonical ? localizedPairByCanonical.get(canonical) : null;
  const imageUrl = image.startsWith('http') ? image : `${SITE_URL}${image}`;
  const jsonBlocks = jsonLd
    .filter(Boolean)
    .map((item) => `    <script type="application/ld+json">${JSON.stringify({
      ...item,
      inLanguage: item.inLanguage || locale,
    })}</script>`)
    .join('\n');
  const canonicalTags = canonical ? [
    `    <link rel="canonical" href="${htmlEscape(canonical)}" />`,
    `    <meta property="og:url" content="${htmlEscape(canonical)}" />`,
  ] : [];
  const alternateTags = localizedPair ? [
    `    <link rel="alternate" hreflang="ru" href="${htmlEscape(localizedPair.ru)}" />`,
    `    <link rel="alternate" hreflang="en" href="${htmlEscape(localizedPair.en)}" />`,
    `    <link rel="alternate" hreflang="x-default" href="${htmlEscape(localizedPair.ru)}" />`,
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
    ...alternateTags,
    `    <meta property="og:site_name" content="${SITE_NAME}" />`,
    `    <meta property="og:locale" content="${locale === 'en' ? 'en_US' : 'ru_RU'}" />`,
    `    <meta property="og:locale:alternate" content="${locale === 'en' ? 'ru_RU' : 'en_US'}" />`,
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

const platformLink = (placement, label = null, className = 'hero-cta', locale = 'ru') => {
  const href = SELF_SERVICE_PUBLIC_ENABLED
    ? getPlatformStartPath(locale)
    : getPublicPath('gettingStarted', locale);
  const resolvedLabel = label || (SELF_SERVICE_PUBLIC_ENABLED
    ? (locale === 'en' ? 'Start for free' : 'Начать бесплатно')
    : (locale === 'en' ? 'Getting started' : 'Как начать'));
  return `<a class="${className}" href="${href}" data-platform-placement="${htmlEscape(placement)}">${htmlEscape(resolvedLabel)}</a>`;
};

const leadCta = (
  placement,
  title = 'Начните с первого устройства',
  text = 'Войдите, подключите Zigbee2MQTT и соберите первую зону самостоятельно. GrowerHub доступен бесплатно и без карты.',
  locale = 'ru',
) => `
      <section class="lead-cta">
        <div>
          <h2>${htmlEscape(title)}</h2>
          <p>${htmlEscape(text)}</p>
        </div>
        <div class="cta-row">
          ${platformLink(placement, null, 'hero-cta', locale)}
          ${telegramLink(`${placement}_help`, locale === 'en' ? 'Telegram support' : 'Помощь в Telegram')}
        </div>
      </section>`;

const staticLayout = (mainHtml, locale = 'ru', canonical = null) => {
  const en = locale === 'en';
  const pair = canonical ? localizedPairByCanonical.get(canonical) : null;
  const switchHref = pair ? (en ? pair.ru : pair.en) : (en ? SITE_URL : `${SITE_URL}/en/`);
  return `
    <div class="app-shell">
      <header class="app-header">
        <div class="brand">
          <a href="${getPublicPath('home', locale)}" class="brand-link">GrowerHub</a>
          <span class="brand-tagline">${en ? 'Manage your farm from one dashboard' : 'Управление фермой в одном кабинете'}</span>
        </div>
        <button class="menu-toggle" type="button" aria-label="${en ? 'Toggle menu' : 'Переключить меню'}">≡</button>
        <nav class="nav-links">
          <a class="nav-link" href="${getPublicPath('home', locale)}">${en ? 'Home' : 'Главная'}</a>
          <a class="nav-link" href="${getPublicPath('gettingStarted', locale)}">${en ? 'Getting started' : 'Как начать'}</a>
          <a class="nav-link" href="${getPublicPath('equipment', locale)}">${en ? 'Equipment' : 'Оборудование'}</a>
          <a class="nav-link" href="${getPublicPath('articles', locale)}">${en ? 'Guides' : 'Статьи'}</a>
          <a class="nav-link app-link" href="/app/?lang=${locale}">${en ? 'Sign in' : 'Вход'}</a>
          ${platformLink('header', null, 'nav-link contact-link', locale)}
          ${telegramLink('header_help', en ? 'Help' : 'Помощь', 'nav-link')}
          <a class="nav-link locale-switch" href="${htmlEscape(switchHref)}" hreflang="${en ? 'ru' : 'en'}">${en ? 'RU' : 'EN'}</a>
        </nav>
      </header>
      <main class="app-main">
        <div class="section static-page">
${mainHtml}
        </div>
      </main>
      <footer class="app-footer">
        <p>© ${new Date().getFullYear()} GrowerHub. ${en ? 'All rights reserved.' : 'Все права защищены.'}</p>
        <div class="footer-links"><a href="${getPublicPath('about', locale)}">${en ? 'About' : 'О проекте'}</a><a href="${getPublicPath('privacy', locale)}">${en ? 'Privacy' : 'Конфиденциальность'}</a><a href="${getPublicPath('terms', locale)}">${en ? 'Terms' : 'Условия'}</a><a href="${TELEGRAM_CHANNEL_URL}" target="_blank" rel="noreferrer">${en ? 'Telegram channel' : 'Telegram-канал'}</a></div>
      </footer>
    </div>
  `;
};

const pageShell = (template, meta, mainHtml, assets) => {
  const locale = meta.locale || (meta.canonical && new URL(meta.canonical).pathname.startsWith('/en/') ? 'en' : 'ru');
  return replaceHtmlLang(
    replaceRoot(
      replaceHead(template, makeMetaHead({ ...meta, locale, assets })),
      staticLayout(mainHtml, locale, meta.canonical),
    ),
    locale,
  );
};

const appShell = (template, meta, assets) => replaceHtmlLang(
  replaceHead(
    template,
    makeMetaHead({ ...meta, robots: 'noindex,nofollow', assets }),
  ),
  meta.locale || 'ru',
);

const formatDate = (date) => (date ? new Date(date).toLocaleDateString('ru-RU') : '');
const formatLocalizedDate = (date, locale = 'ru') => (
  date ? new Date(date).toLocaleDateString(locale === 'en' ? 'en-GB' : 'ru-RU') : ''
);

const pageDataScript = (article) => {
  const payload = JSON.stringify({
    articleId: article.id,
    locale: article.locale,
    bodyHtml: article.bodyHtml,
  }).replace(/</g, '\\u003c');
  return `<script type="application/json" id="growerhub-page-data">${payload}</script>`;
};

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
          <div class="content-section"><a class="secondary-link" href="/articles/">Назад к статьям</a></div>
          ${pageDataScript(article)}`;

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
        areaServed: 'Россия и страны СНГ',
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
        areaServed: 'Россия и страны СНГ',
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
          <section class="content-section info-block"><h2>Главное — выбрать Zigbee</h2><p>${htmlEscape(equipment.zigbee_note)}</p></section>
          <section class="content-section"><h2>Выберите раздел</h2><div class="card-grid">
${categories.map((category) => `            <article class="card"><h3>${htmlEscape(category.title)}</h3><p>${htmlEscape(category.intro)}</p><a class="secondary-link" href="/oborudovanie/${htmlEscape(category.slug)}/">Посмотреть варианты</a></article>`).join('\n')}
            <article class="card"><h3>${htmlEscape(equipment.pump.title)}</h3><p>${htmlEscape(equipment.pump.summary)}</p><a class="secondary-link" href="/oborudovanie/nasos-dlya-poliva/">О насосе GrowerHub</a></article>
          </div></section>
          <section class="content-section info-block"><h2>Почему не любой Wi‑Fi-датчик</h2><p>${htmlEscape(equipment.wifi_note)}</p></section>
          ${leadCta('equipment_index_bottom')}`;

  return pageShell(template, {
    title: 'Оборудование для GrowerHub — доступные Zigbee-варианты',
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
${category.items.map((item) => renderEquipmentCard(item, 'ru')).join('\n')}
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

const renderEnglishArticleCard = (article) => `
          <article class="article-card">
            <div class="article-meta">Updated ${htmlEscape(formatLocalizedDate(article.updated_at, 'en'))}</div>
            <a href="${htmlEscape(getArticlePath(article, 'en'))}">${htmlEscape(article.title)}</a>
            <p>${htmlEscape(article.summary)}</p>
          </article>`;

const localizedArticleLd = (article, cluster, locale) => {
  const canonical = toCanonicalUrl(getArticlePath(article, locale));
  return {
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    headline: article.title,
    description: article.summary,
    datePublished: article.created_at,
    dateModified: article.updated_at,
    image: article.hero_image ? `${SITE_URL}${article.hero_image}` : `${SITE_URL}${DEFAULT_OG_IMAGE}`,
    mainEntityOfPage: canonical,
    articleSection: cluster?.title,
    keywords: article.keywords,
    author: organizationLd,
    publisher: organizationLd,
  };
};

const localizedCollectionLd = (title, description, pathName, articles, locale) => ({
  '@context': 'https://schema.org',
  '@type': 'CollectionPage',
  name: title,
  description,
  url: toCanonicalUrl(pathName),
  hasPart: articles.map((article) => ({
    '@type': 'Article',
    headline: article.title,
    url: toCanonicalUrl(getArticlePath(article, locale)),
  })),
});

const renderEnglishArticlePage = (
  template,
  assets,
  article,
  articlesById,
  clustersById,
) => {
  const cluster = clustersById.get(article.cluster);
  const related = article.related
    .map((id) => articlesById.get(id))
    .filter(Boolean)
    .slice(0, 4);
  const articlePath = getArticlePath(article, 'en');
  const canonical = toCanonicalUrl(articlePath);
  const heroInBody = article.hero_image && article.body.includes(`](${article.hero_image})`);
  const mainHtml = `
          <div class="article-meta">Updated ${htmlEscape(formatLocalizedDate(article.updated_at, 'en'))}</div>
          <h1>${htmlEscape(article.title)}</h1>
          <p class="article-lead">${htmlEscape(article.summary)}</p>
          ${cluster ? `<p><a class="secondary-link" href="${htmlEscape(getClusterPath(cluster, 'en'))}">${htmlEscape(cluster.title)}</a></p>` : ''}
          ${article.hero_image && !heroInBody ? `<img class="article-hero-image" src="${htmlEscape(article.hero_image)}" alt="${htmlEscape(article.hero_alt)}" />` : ''}
          <div class="article-body">
${article.bodyHtml}
          </div>
          ${leadCta(
    'article_bottom',
    'Connect a device to GrowerHub',
    'Sign in, connect Zigbee2MQTT, and see device metrics in one dashboard. If you need help, message us in Telegram in Russian or English.',
    'en',
  )}
          ${related.length ? `
          <section class="related-articles">
            <h2>Related guides</h2>
            <div class="articles-list">
${related.map(renderEnglishArticleCard).join('\n')}
            </div>
          </section>` : ''}
          <div class="content-section"><a class="secondary-link" href="${getPublicPath('articles', 'en')}">Back to guides</a></div>
          ${pageDataScript(article)}`;

  return pageShell(template, {
    title: `${article.title} — GrowerHub`,
    description: article.summary,
    canonical,
    type: 'article',
    image: article.hero_image || DEFAULT_OG_IMAGE,
    locale: 'en',
    jsonLd: [
      localizedArticleLd(article, cluster, 'en'),
      breadcrumbLd([
        { name: 'GrowerHub', url: toCanonicalUrl('/en/') },
        { name: 'Guides', url: toCanonicalUrl(getPublicPath('articles', 'en')) },
        ...(cluster ? [{
          name: cluster.title,
          url: toCanonicalUrl(getClusterPath(cluster, 'en')),
        }] : []),
        { name: article.title, url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderEnglishArticlesIndex = (template, assets, clusters, articlesByCluster) => {
  const routePath = getPublicPath('articles', 'en');
  const canonical = toCanonicalUrl(routePath);
  const allArticles = [...articlesByCluster.values()].flat();
  const description = 'Practical GrowerHub guides to irrigation, sensors, Zigbee2MQTT, Home Assistant, greenhouse monitoring, and small-farm automation.';
  const mainHtml = `
          <h1>GrowerHub guides</h1>
          <p>${description}</p>
          <div class="cluster-list">
${clusters.map((cluster) => `
            <section class="cluster-block">
              <div class="cluster-block__header">
                <div><h2>${htmlEscape(cluster.title)}</h2><p>${htmlEscape(cluster.description)}</p></div>
                <a class="secondary-link" href="${htmlEscape(getClusterPath(cluster, 'en'))}">View section</a>
              </div>
              <div class="cluster-meta-grid">
                <div><strong>A good fit if</strong><p>${htmlEscape(cluster.fit)}</p></div>
                <div><strong>Topics covered</strong><p>${htmlEscape(cluster.tasks)}</p></div>
              </div>
              <div class="articles-list">
${(articlesByCluster.get(cluster.id) || []).map(renderEnglishArticleCard).join('\n')}
              </div>
            </section>`).join('\n')}
          </div>
          ${leadCta(
    'articles_index_bottom',
    'Start with your first device',
    'Connect Zigbee2MQTT, discover your devices, and create the first zone. GrowerHub is free to use in early access.',
    'en',
  )}`;

  return pageShell(template, {
    title: 'GrowerHub guides — sensors, irrigation, and automation',
    description,
    canonical,
    locale: 'en',
    jsonLd: [
      localizedCollectionLd('GrowerHub guides', description, routePath, allArticles, 'en'),
      breadcrumbLd([
        { name: 'GrowerHub', url: toCanonicalUrl('/en/') },
        { name: 'Guides', url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderEnglishClusterPage = (template, assets, cluster, articles, otherClusters) => {
  const routePath = getClusterPath(cluster, 'en');
  const canonical = toCanonicalUrl(routePath);
  const mainHtml = `
          <div class="article-meta">Practical guide section</div>
          <h1>${htmlEscape(cluster.title)}</h1>
          <p>${htmlEscape(cluster.description)}</p>
          <div class="cluster-meta-grid">
            <div><strong>A good fit if</strong><p>${htmlEscape(cluster.fit)}</p></div>
            <div><strong>Topics covered</strong><p>${htmlEscape(cluster.tasks)}</p></div>
          </div>
          <div class="keyword-list">
${cluster.keywords.map((keyword) => `            <span>${htmlEscape(keyword)}</span>`).join('\n')}
          </div>
          <section class="cluster-block">
            <h2>Guides in this section</h2>
            <div class="articles-list">
${articles.map(renderEnglishArticleCard).join('\n')}
            </div>
          </section>
          ${leadCta(
    'cluster_bottom',
    'Start with your first device',
    'Connect Zigbee2MQTT, discover your devices, and create the first zone. We can help in Telegram in Russian or English.',
    'en',
  )}
          <section class="cluster-block">
            <h2>Other sections</h2>
            <div class="cluster-nav-grid">
${otherClusters.map((item) => `              <a href="${htmlEscape(getClusterPath(item, 'en'))}">${htmlEscape(item.title)}</a>`).join('\n')}
            </div>
          </section>`;

  return pageShell(template, {
    title: `${cluster.title} — GrowerHub guides`,
    description: cluster.description,
    canonical,
    locale: 'en',
    jsonLd: [
      localizedCollectionLd(cluster.title, cluster.description, routePath, articles, 'en'),
      breadcrumbLd([
        { name: 'GrowerHub', url: toCanonicalUrl('/en/') },
        { name: 'Guides', url: toCanonicalUrl(getPublicPath('articles', 'en')) },
        { name: cluster.title, url: canonical },
      ]),
    ],
  }, mainHtml, assets);
};

const renderEnglishHomePage = (
  template,
  assets,
  homeContent,
  articles,
  articlesById,
  clusters,
) => {
  const { hero, secondary, features } = homeContent;
  const routePath = getPublicPath('home', 'en');
  const canonical = toCanonicalUrl(routePath);
  const description = 'GrowerHub brings practical greenhouse automation experience into one platform for Zigbee devices, zones, sensor history, and safe control scenarios.';
  const mainHtml = `
          <div class="hero">
            <div>
              <div class="badge">${htmlEscape(hero.badge)}</div>
              <h1>${htmlEscape(hero.title)}</h1>
              <p>${htmlEscape(hero.subtitle)}</p>
              <div class="cta-row">
                ${platformLink('home_hero', SELF_SERVICE_PUBLIC_ENABLED ? hero.cta : 'Getting started', 'hero-cta', 'en')}
                <a class="secondary-link" href="${getPublicPath('gettingStarted', 'en')}">Setup path</a>
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
            <h2>Extensive automation experience in one platform</h2>
            <p>${htmlEscape(homeContent.early_access)}</p>
            <div class="cta-row"><a class="secondary-link" href="${getPublicPath('equipment', 'en')}">Choose equipment</a><a class="secondary-link" href="${getPublicPath('farmAutomation', 'en')}">Explore platform features</a></div>
          </section>
          <section class="content-section">
            <h2>Practical sections</h2>
            <div class="cluster-home-grid">
${clusters.map((cluster) => {
    const featured = cluster.featuredArticles
      .map((id) => articlesById.get(id))
      .filter(Boolean)
      .slice(0, 4);
    return `              <article class="article-card">
                <a href="${htmlEscape(getClusterPath(cluster, 'en'))}">${htmlEscape(cluster.title)}</a>
                <p>${htmlEscape(cluster.description)}</p>
                <ul class="compact-link-list">${featured.map((article) => `<li><a href="${htmlEscape(getArticlePath(article, 'en'))}">${htmlEscape(article.title)}</a></li>`).join('')}</ul>
              </article>`;
  }).join('\n')}
            </div>
          </section>
          <section class="content-section">
            <div class="cluster-block__header"><div><h2>Recent guides</h2><p>Step-by-step Zigbee, Home Assistant, sensor, and safe irrigation guides.</p></div><a class="secondary-link" href="${getPublicPath('articles', 'en')}">All guides</a></div>
            <div class="articles-list">
${articles.slice(0, 4).map(renderEnglishArticleCard).join('\n')}
            </div>
          </section>
          ${leadCta(
    'home_bottom',
    'Start with your first device',
    'Connect Zigbee2MQTT, discover your devices, and create the first zone. GrowerHub is free to use in early access.',
    'en',
  )}`;

  return pageShell(template, {
    title: 'GrowerHub — small-farm management platform',
    description,
    canonical,
    locale: 'en',
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        name: SITE_NAME,
        url: canonical,
      },
      {
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: SITE_NAME,
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        url: canonical,
        description,
        areaServed: 'Russia and CIS countries',
        ...(SELF_SERVICE_PUBLIC_ENABLED ? {
          isAccessibleForFree: true,
          offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
        } : {}),
      },
    ],
  }, mainHtml, assets);
};

const renderEnglishAboutPage = (template, assets, data) => {
  const routePath = getPublicPath('about', 'en');
  const canonical = toCanonicalUrl(routePath);
  return pageShell(template, {
    title: 'About GrowerHub — greenhouse automation platform',
    description: data.intro,
    canonical,
    locale: 'en',
    jsonLd: [breadcrumbLd([
      { name: SITE_NAME, url: toCanonicalUrl('/en/') },
      { name: 'About', url: canonical },
    ])],
  }, `
          <h1>${htmlEscape(data.title)}</h1>
          <p>${htmlEscape(data.intro)}</p>
          <div class="info-grid content-section">
            <div class="info-block"><h2>Our goal</h2><p>${htmlEscape(data.mission)}</p></div>
            <div class="info-block"><h2>How we help</h2><p>${htmlEscape(data.value)}</p></div>
          </div>
          <section class="content-section"><h2>Contact</h2><ul class="contact-list"><li><strong>Website:</strong> <a href="${htmlEscape(data.contacts.site)}">${htmlEscape(data.contacts.site)}</a></li><li><strong>Telegram:</strong> <a href="${TELEGRAM_CHANNEL_URL}" target="_blank" rel="noreferrer">GrowerHub channel</a></li></ul></section>
          ${leadCta(
    'about_bottom',
    'Start with your first device',
    'Connect your equipment independently, or ask us for help in Telegram in Russian or English.',
    'en',
  )}`, assets);
};

const renderEnglishMiniFarmPage = (template, assets, data) => {
  const routePath = getPublicPath('farmAutomation', 'en');
  const canonical = toCanonicalUrl(routePath);
  const screenFiles = ['zones', 'history', 'connection', 'automation'];
  return pageShell(template, {
    title: `${data.title} — GrowerHub`,
    description: data.description,
    canonical,
    locale: 'en',
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: SITE_NAME,
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        url: canonical,
        description: data.description,
        areaServed: 'Russia and CIS countries',
        ...(SELF_SERVICE_PUBLIC_ENABLED ? {
          isAccessibleForFree: true,
          offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
        } : {}),
      },
      breadcrumbLd([
        { name: SITE_NAME, url: toCanonicalUrl('/en/') },
        { name: data.title, url: canonical },
      ]),
    ],
  }, `
          <section class="landing-hero"><div><div class="badge">${htmlEscape(data.hero.eyebrow)}</div><h1>${htmlEscape(data.title)}</h1><p>${htmlEscape(data.hero.text)}</p><div class="cta-row">${platformLink('farm_hero', data.hero.cta, 'hero-cta', 'en')}${telegramLink('farm_hero_help', 'Telegram support')}</div></div><aside class="landing-summary"><strong>Early access is open</strong><p>${htmlEscape(data.early_access)}</p></aside></section>
          <section class="content-section"><h2>Farm tasks in one dashboard</h2><div class="card-grid">${data.tasks.map((item) => `<article class="card"><h3>${htmlEscape(item.title)}</h3><p>${htmlEscape(item.text)}</p></article>`).join('')}</div></section>
          <section class="content-section split-section"><div><h2>Platform features</h2><ul class="check-list">${data.capabilities.map((item) => `<li>${htmlEscape(item)}</li>`).join('')}</ul></div><div class="info-block"><h2>${htmlEscape(data.compatibility.title)}</h2><p>${htmlEscape(data.compatibility.text)}</p><a class="secondary-link" href="${getPublicPath('equipment', 'en')}">Choose equipment</a></div></section>
          <section class="content-section"><h2>From sign-in to dashboard</h2><ol class="steps-list">${data.stages.map((step) => `<li><strong>${htmlEscape(step.title)}</strong><span>${htmlEscape(step.text)}</span></li>`).join('')}</ol></section>
          <section class="content-section"><h2>GrowerHub interface</h2><div class="demo-grid">${data.screens.map((screen, index) => `<figure class="demo-card"><img src="/screenshots/en/${screenFiles[index]}.png" alt="${htmlEscape(screen.title)} — synthetic GrowerHub interface" width="1440" height="900" loading="lazy" decoding="async" /><figcaption><strong>${htmlEscape(screen.title)}</strong><span>${htmlEscape(screen.text)}</span></figcaption></figure>`).join('')}</div></section>
          <section class="content-section"><h2>Clear and safe boundaries</h2><ul class="check-list limitations-list">${data.limitations.map((item) => `<li>${htmlEscape(item)}</li>`).join('')}</ul></section>
          ${leadCta(
    'farm_bottom',
    'Connect equipment and open your dashboard',
    'Create a connection, run Zigbee2MQTT, and see devices automatically. We can help in Telegram in Russian or English.',
    'en',
  )}`, assets);
};

const renderEnglishGettingStartedPage = (template, assets, data) => {
  const { start, minimum, early_access_text: earlyAccessText } = data;
  const routePath = getPublicPath('gettingStarted', 'en');
  const canonical = toCanonicalUrl(routePath);
  return pageShell(template, {
    title: `${start.title} — Zigbee2MQTT`,
    description: start.description,
    canonical,
    locale: 'en',
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
      breadcrumbLd([
        { name: SITE_NAME, url: toCanonicalUrl('/en/') },
        { name: start.title, url: canonical },
      ]),
    ],
  }, `
          <section class="landing-hero"><div><div class="badge">Self-service setup</div><h1>${htmlEscape(start.title)}</h1><p>${htmlEscape(start.intro)}</p>${leadCta('getting_started_hero', 'Start with your first device', 'Sign in, connect Zigbee2MQTT, and create your first zone.', 'en')}</div><aside class="landing-summary"><strong>Early access is open</strong><p>${htmlEscape(earlyAccessText)}</p></aside></section>
          <section class="content-section"><h2>Seven short steps</h2><ol class="steps-list">${start.steps.map((step) => `<li><strong>${htmlEscape(step.title)}</strong><span>${htmlEscape(step.text)}</span></li>`).join('')}</ol></section>
          <section class="content-section split-section"><div><h2>${htmlEscape(minimum.title)}</h2><div class="info-grid"><div class="info-block"><h3>Monitoring only</h3><p>${htmlEscape(minimum.monitoring)}</p></div><div class="info-block"><h3>Control</h3><p>${htmlEscape(minimum.control)}</p></div></div></div><div class="info-block"><h2>Already using Home Assistant?</h2><p>${htmlEscape(minimum.existing)}</p><a class="secondary-link" href="${getPublicPath('equipmentCoordinators', 'en')}">Check equipment</a></div></section>
          <section class="content-section"><h2>We can help with setup</h2><p>If something does not connect or you want to understand a feature faster, message us in Telegram. The GrowerHub team can help at any stage in Russian or English.</p>${telegramLink('getting_started_help', 'Telegram support')}</section>
          ${leadCta(
    'getting_started_bottom',
    'Start with your first device',
    'Connect your equipment independently and build the first zone at your own pace.',
    'en',
  )}`, assets);
};

const renderEquipmentCard = (item, locale) => {
  const en = locale === 'en';
  return `            <article class="equipment-card">
              ${item.image ? `<figure class="equipment-card__media"><img src="${htmlEscape(item.image)}" alt="${htmlEscape(item.image_alt || item.model)}" width="640" height="420" loading="lazy" decoding="async" />${item.image_caption ? `<figcaption>${htmlEscape(item.image_caption)}</figcaption>` : ''}</figure>` : ''}
              <div><span class="status-chip">${htmlEscape(item.status)}</span><h2>${htmlEscape(item.model)}</h2><h3>${htmlEscape(item.name)}</h3><p>${htmlEscape(item.summary)}</p></div>
              <ul class="check-list">${item.notes.map((note) => `<li>${htmlEscape(note)}</li>`).join('')}</ul>
              <div class="cta-row"><a class="secondary-link" href="${htmlEscape(item.official_url)}" target="_blank" rel="noreferrer">${en ? 'Zigbee2MQTT compatibility' : 'Совместимость Zigbee2MQTT'}</a>${!en && item.example_url ? `<a class="secondary-link" href="${htmlEscape(item.example_url)}" target="_blank" rel="nofollow noreferrer">Пример на Ozon</a>` : ''}<a class="secondary-link" href="${htmlEscape(item.shop_search_url)}" target="_blank" rel="nofollow noreferrer">${en ? 'Search exact model' : 'Найти на Ozon'}</a></div>
            </article>`;
};

const renderEnglishEquipmentIndexPage = (template, assets, equipment, platformContent) => {
  const routePath = getPublicPath('equipment', 'en');
  const canonical = toCanonicalUrl(routePath);
  const categoryRouteIds = {
    coordinators: 'equipmentCoordinators',
    sensors: 'equipmentSensors',
    sockets: 'equipmentSockets',
  };
  const description = 'Soft equipment recommendations for connecting Zigbee2MQTT to GrowerHub, from a coordinator and one sensor to smart plugs and optional extensions.';
  return pageShell(template, {
    title: 'Equipment for GrowerHub — Zigbee coordinators, sensors, and smart plugs',
    description,
    canonical,
    locale: 'en',
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: 'Equipment for GrowerHub',
        description,
        url: canonical,
        isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: toCanonicalUrl('/en/') },
        mainEntity: {
          '@type': 'ItemList',
          itemListElement: Object.entries(equipment.categories).map(([key, category], index) => ({
            '@type': 'ListItem',
            position: index + 1,
            name: category.title,
            url: toCanonicalUrl(getPublicPath(categoryRouteIds[key], 'en')),
          })),
        },
      },
      breadcrumbLd([
        { name: SITE_NAME, url: toCanonicalUrl('/en/') },
        { name: 'Equipment', url: canonical },
      ]),
    ],
  }, `
          <div class="badge">No mandatory kits</div>
          <h1>Equipment for GrowerHub</h1>
          <p class="article-lead">${description}</p>
          <section class="content-section start-kit"><h2>Minimum setup</h2><div class="card-grid"><article class="card"><h3>Monitoring</h3><p>${htmlEscape(platformContent.minimum.monitoring)}</p></article><article class="card"><h3>Control</h3><p>${htmlEscape(platformContent.minimum.control)}</p></article><article class="card"><h3>Keep an existing setup</h3><p>${htmlEscape(platformContent.minimum.existing)}</p></article></div></section>
          <section class="content-section info-block"><h2>Choose Zigbee first</h2><p>${htmlEscape(equipment.zigbee_note)}</p></section>
          <section class="content-section"><h2>Choose a category</h2><div class="card-grid">${Object.entries(equipment.categories).map(([key, category]) => `<article class="card"><h3>${htmlEscape(category.title)}</h3><p>${htmlEscape(category.intro)}</p><a class="secondary-link" href="${getPublicPath(categoryRouteIds[key], 'en')}">View recommendations</a></article>`).join('')}<article class="card"><h3>${htmlEscape(equipment.pump.title)}</h3><p>${htmlEscape(equipment.pump.summary)}</p><a class="secondary-link" href="${getPublicPath('equipmentPump', 'en')}">About the GrowerHub pump</a></article></div></section>
          <section class="content-section info-block"><h2>Why not every Wi-Fi sensor works</h2><p>${htmlEscape(equipment.wifi_note)}</p></section>
          ${leadCta(
    'equipment_index_bottom',
    'Start with your first device',
    'A supported coordinator and one Zigbee sensor are enough to begin monitoring.',
    'en',
  )}`, assets);
};

const renderEnglishEquipmentCategoryPage = (
  template,
  assets,
  equipment,
  categoryKey,
) => {
  const category = equipment.categories[categoryKey];
  const routeIds = {
    coordinators: 'equipmentCoordinators',
    sensors: 'equipmentSensors',
    sockets: 'equipmentSockets',
  };
  const routePath = getPublicPath(routeIds[categoryKey], 'en');
  const canonical = toCanonicalUrl(routePath);
  return pageShell(template, {
    title: `${category.title} — GrowerHub`,
    description: category.description,
    canonical,
    locale: 'en',
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: category.title,
        description: category.description,
        url: canonical,
        isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: toCanonicalUrl('/en/') },
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
        { name: SITE_NAME, url: toCanonicalUrl('/en/') },
        { name: 'Equipment', url: toCanonicalUrl(getPublicPath('equipment', 'en')) },
        { name: category.title, url: canonical },
      ]),
    ],
  }, `
          <a class="secondary-link" href="${getPublicPath('equipment', 'en')}">← All equipment</a>
          <div class="badge">Checked ${htmlEscape(formatLocalizedDate(equipment.checked_at, 'en'))}</div>
          <h1>${htmlEscape(category.title)}</h1>
          <p class="article-lead">${htmlEscape(category.intro)}</p>
          <p class="equipment-disclaimer">${htmlEscape(equipment.purchase_note)}</p>
          <div class="equipment-list content-section">
${category.items.map((item) => renderEquipmentCard(item, 'en')).join('\n')}
          </div>
          ${categoryKey === 'sensors' ? `<section class="content-section info-block"><h2>Wi-Fi sensors</h2><p>${htmlEscape(equipment.wifi_note)}</p></section>` : ''}
          ${leadCta(
    `equipment_${category.slug}_bottom`,
    'Start with compatible equipment',
    'Connect a coordinator and one Zigbee sensor first. You can add more devices later.',
    'en',
  )}`, assets);
};

const renderEnglishPumpEarlyAccessPage = (template, assets, equipment) => {
  const pump = equipment.pump;
  const routePath = getPublicPath('equipmentPump', 'en');
  const canonical = toCanonicalUrl(routePath);
  return pageShell(template, {
    title: `${pump.title} — GrowerHub`,
    description: pump.description,
    canonical,
    locale: 'en',
    jsonLd: [
      {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        name: pump.title,
        description: pump.description,
        url: canonical,
        isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: toCanonicalUrl('/en/') },
      },
      breadcrumbLd([
        { name: SITE_NAME, url: toCanonicalUrl('/en/') },
        { name: 'Equipment', url: toCanonicalUrl(getPublicPath('equipment', 'en')) },
        { name: pump.title, url: canonical },
      ]),
    ],
  }, `
          <div class="badge">${htmlEscape(pump.status)}</div><h1>${htmlEscape(pump.title)}</h1><p class="article-lead">${htmlEscape(pump.summary)}</p>
          <section class="content-section split-section"><div class="info-block"><h2>Two connection options</h2><p>We are designing Zigbee connectivity for the shared device mesh and Wi-Fi with direct GrowerHub MQTT. Both options use the same platform dashboard.</p></div><div class="info-block"><h2>Current stage</h2><p>The prototype is being tested. If you would like to join the first users, message us and we will discuss your equipment and a suitable scenario.</p></div></section>
          <section class="content-section"><h2>What we are testing</h2><ul class="check-list limitations-list">${pump.limitations.map((item) => `<li>${htmlEscape(item)}</li>`).join('')}</ul></section>
          <section class="lead-cta"><div><h2>Become an early user</h2><p>Tell us where you plan to use the pump. We will answer your questions and help you prepare for testing.</p></div>${telegramLink('pump_early_access', 'Message us on Telegram', 'hero-cta')}</section>
          ${leadCta(
    'pump_platform_bottom',
    'Start with available equipment',
    'You do not need a pump for monitoring: a coordinator and one Zigbee sensor are enough.',
    'en',
  )}`, assets);
};

const renderEnglishLegalPage = (template, assets, legal, type) => {
  const privacy = type === 'privacy';
  const routePath = getPublicPath(type, 'en');
  const title = privacy ? legal.privacy_title : legal.terms_title;
  const canonical = toCanonicalUrl(routePath);
  const body = privacy ? `
          <h1>${htmlEscape(title)}</h1><p>Operator: ${htmlEscape(legal.operator_name)}. Contact: ${htmlEscape(legal.operator_contact)}.</p>
          <h2>Data we process</h2><p>Email address and the selected sign-in provider identifier, technical connection and device data, security events, and zone or automation settings.</p>
          <h2>Purpose</h2><p>Sign-in, platform operation, user-space protection, diagnostics, and support when requested by the user.</p>
          <h2>Connection secrets</h2><p>The one-time MQTT password is shown when credentials are created or rotated and is not stored in plain text by GrowerHub. Local Home Assistant credentials stay in the browser and are used only to create a downloadable local file.</p>
          <h2>Requests</h2><p>Use the operator contact for data or account deletion requests.</p>` : `
          <h1>${htmlEscape(title)}</h1><p>Operator: ${htmlEscape(legal.operator_name)}. Contact: ${htmlEscape(legal.operator_contact)}.</p>
          <h2>Early access</h2><p>GrowerHub is currently available without a payment card. Core features work today while the supported device and scenario catalog continues to grow. We will announce important changes in advance.</p>
          <h2>Connecting equipment</h2><p>For equipment around water and mains power, follow electrical-safety rules, verify the load, and provide a physical emergency shutdown.</p>
          <h2>Support</h2><p>If you need help, message us on Telegram. We can help with setup, devices, and platform features in Russian or English.</p>`;
  return pageShell(template, {
    title,
    description: `${title} for GrowerHub platform users.`,
    canonical,
    robots: 'noindex,follow',
    locale: 'en',
  }, body, assets);
};

const renderEnglish404 = (template, assets) => pageShell(template, {
  title: 'Page not found — GrowerHub',
  description: 'The requested GrowerHub page was not found.',
  canonical: null,
  robots: 'noindex,nofollow',
  locale: 'en',
}, `
          <div class="article-meta">Error 404</div>
          <h1>Page not found</h1>
          <p>The requested GrowerHub page was not found. Return to the English home page or browse practical guides.</p>
          <div class="cta-row"><a class="hero-cta" href="/en/">Home</a><a class="secondary-link" href="/en/articles/">Guides</a></div>`, assets);

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

const buildSitemapEntries = ({
  locale,
  articles,
  clusters,
  articlesByCluster,
  homeContent,
  aboutContent,
  miniFarmContent,
  platformContent,
  equipment,
}) => {
  const latestArticleDate = maxDate(articles.map((article) => article.updated_at));
  const entries = [
    { loc: toCanonicalUrl(getPublicPath('home', locale)), lastmod: homeContent.updated_at },
    { loc: toCanonicalUrl(getPublicPath('about', locale)), lastmod: aboutContent.updated_at },
    { loc: toCanonicalUrl(getPublicPath('articles', locale)), lastmod: latestArticleDate },
    {
      loc: toCanonicalUrl(getPublicPath('farmAutomation', locale)),
      lastmod: miniFarmContent.updated_at,
    },
    {
      loc: toCanonicalUrl(getPublicPath('gettingStarted', locale)),
      lastmod: platformContent.updated_at,
    },
    { loc: toCanonicalUrl(getPublicPath('equipment', locale)), lastmod: equipment.updated_at },
    {
      loc: toCanonicalUrl(getPublicPath('equipmentCoordinators', locale)),
      lastmod: equipment.updated_at,
    },
    {
      loc: toCanonicalUrl(getPublicPath('equipmentSensors', locale)),
      lastmod: equipment.updated_at,
    },
    {
      loc: toCanonicalUrl(getPublicPath('equipmentSockets', locale)),
      lastmod: equipment.updated_at,
    },
    {
      loc: toCanonicalUrl(getPublicPath('equipmentPump', locale)),
      lastmod: equipment.updated_at,
    },
    ...clusters.map((cluster) => ({
      loc: toCanonicalUrl(getClusterPath(cluster, locale)),
      lastmod: maxDate(
        (articlesByCluster.get(cluster.id) || []).map((article) => article.updated_at),
      ),
    })),
    ...articles.map((article) => ({
      loc: toCanonicalUrl(getArticlePath(article, locale)),
      lastmod: article.updated_at,
    })),
  ];

  if (entries.length !== 69) {
    throw new Error(`Expected 69 ${locale} sitemap URLs, got ${entries.length}`);
  }

  return entries;
};

const writeSitemap = (entries) => {
  if (entries.length !== 138) {
    throw new Error(`Expected 138 public sitemap URLs, got ${entries.length}`);
  }
  const uniqueLocations = new Set(entries.map((entry) => entry.loc));
  if (uniqueLocations.size !== entries.length) {
    throw new Error('Sitemap contains duplicate locations');
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

const outputPathForRoute = (routePath) => {
  const segments = toCanonicalPath(routePath).split('/').filter(Boolean);
  return segments.length
    ? path.join(DIST_DIR, ...segments, 'index.html')
    : path.join(DIST_DIR, 'index.html');
};

const writePublicPage = (routePath, html) => writeText(outputPathForRoute(routePath), html);

const localizeEnglishArticleLinks = (
  html,
  ruArticlesBySlug,
  enArticlesById,
  ruClustersBySlug,
  enClustersById,
) => {
  let localized = String(html).replace(
    /href="\/articles\/([^"?#/]+)\/?([?#][^"]*)?"/g,
    (match, slug, suffix = '') => {
      const source = ruArticlesBySlug.get(slug);
      const translated = source ? enArticlesById.get(source.id) : null;
      return translated
        ? `href="${getArticlePath(translated, 'en')}${suffix}"`
        : match;
    },
  );

  localized = localized.replace(
    /href="\/articles\/clusters\/([^"?#/]+)\/?([?#][^"]*)?"/g,
    (match, slug, suffix = '') => {
      const source = ruClustersBySlug.get(slug);
      const translated = source ? enClustersById.get(source.id) : null;
      return translated
        ? `href="${getClusterPath(translated, 'en')}${suffix}"`
        : match;
    },
  );

  Object.values(PUBLIC_ROUTES)
    .sort((left, right) => right.ru.length - left.ru.length)
    .forEach((paths) => {
    if (paths.ru === '/') {
      localized = localized.replace(/href="\/([?#])/g, `href="${paths.en}$1`);
      return;
    }
    localized = localized.replaceAll(`href="${paths.ru}`, `href="${paths.en}`);
    });

  return canonicalizePublicLinksInHtml(localized);
};

const main = () => {
  const template = readTemplate();
  const assets = extractHeadAssets(template);
  const articles = readArticles();
  const enArticles = readArticles('en');
  const homeContent = readJson('home.json');
  const enHomeContent = readJson('home.json', 'en');
  const aboutContent = readJson('about.json');
  const enAboutContent = readJson('about.json', 'en');
  const miniFarmContent = readJson('mini-farm.json');
  const enMiniFarmContent = readJson('mini-farm.json', 'en');
  const platformContent = readJson('platform.json');
  const enPlatformContent = readJson('platform.json', 'en');
  const legalContent = readJson('legal.json');
  const enLegalContent = readJson('legal.json', 'en');
  const equipment = readEquipmentCatalog();
  const enEquipment = readEquipmentCatalog('en');
  const enClusters = getArticleClusters('en');

  for (const legal of [legalContent, enLegalContent]) {
    if (SELF_SERVICE_PUBLIC_ENABLED && (
      !legal.reviewed
      || !legal.operator_name
      || !legal.operator_contact
    )) {
      throw new Error('Self-service cannot be published before legal operator data is reviewed');
    }
  }

  if (articles.length !== 54 || enArticles.length !== 54) {
    throw new Error(`Expected 54 articles per locale, got ru=${articles.length}, en=${enArticles.length}`);
  }

  const articlesBySlug = new Map(articles.map((article) => [article.slug, article]));
  const articlesById = new Map(articles.map((article) => [article.id, article]));
  const enArticlesById = new Map(enArticles.map((article) => [article.id, article]));
  const clustersBySlug = new Map(articleClusters.map((cluster) => [cluster.slug, cluster]));
  const enClustersById = new Map(enClusters.map((cluster) => [cluster.id, cluster]));
  const articlesByCluster = new Map(articleClusters.map((cluster) => [
    cluster.id,
    articles.filter((article) => article.cluster === cluster.id),
  ]));
  const enArticlesByCluster = new Map(enClusters.map((cluster) => [
    cluster.id,
    enArticles.filter((article) => article.cluster === cluster.id),
  ]));

  for (const article of articles) {
    const translation = enArticlesById.get(article.id);
    if (!translation) {
      throw new Error(`Missing English translation for article ${article.id}`);
    }
    registerLocalizedPair(
      getArticlePath(article, 'ru'),
      getArticlePath(translation, 'en'),
    );
  }
  if (enArticles.some((article) => !articlesById.has(article.id))) {
    throw new Error('English articles contain an unknown translation_of value');
  }

  for (const cluster of articleClusters) {
    const translation = enClustersById.get(cluster.id);
    if (!translation) {
      throw new Error(`Missing English translation for cluster ${cluster.id}`);
    }
    registerLocalizedPair(
      getClusterPath(cluster, 'ru'),
      getClusterPath(translation, 'en'),
    );
  }

  for (const article of enArticles) {
    article.bodyHtml = localizeEnglishArticleLinks(
      article.bodyHtml,
      articlesBySlug,
      enArticlesById,
      clustersBySlug,
      enClustersById,
    );
  }

  writePublicPage('/', renderHomePage(template, assets, homeContent, articles, articlesBySlug));
  writePublicPage('/about/', renderAboutPage(template, assets, aboutContent));
  writePublicPage('/articles/', renderArticlesIndex(template, assets, articlesByCluster));
  writePublicPage(
    '/avtomatizatsiya-mini-fermy/',
    renderMiniFarmPage(template, assets, miniFarmContent),
  );
  writePublicPage('/kak-nachat/', renderGettingStartedPage(template, assets, platformContent));
  writePublicPage(
    '/oborudovanie/',
    renderEquipmentIndexPage(template, assets, equipment, platformContent),
  );
  writePublicPage(
    '/oborudovanie/zigbee-koordinator/',
    renderEquipmentCategoryPage(template, assets, equipment, 'coordinators'),
  );
  writePublicPage(
    '/oborudovanie/datchiki/',
    renderEquipmentCategoryPage(template, assets, equipment, 'sensors'),
  );
  writePublicPage(
    '/oborudovanie/zigbee-rozetki/',
    renderEquipmentCategoryPage(template, assets, equipment, 'sockets'),
  );
  writePublicPage(
    '/oborudovanie/nasos-dlya-poliva/',
    renderPumpEarlyAccessPage(template, assets, equipment),
  );
  writePublicPage('/privacy/', renderLegalPage(template, assets, legalContent, 'privacy'));
  writePublicPage('/terms/', renderLegalPage(template, assets, legalContent, 'terms'));
  writeText(path.join(DIST_DIR, '404.html'), render404(template, assets));

  writePublicPage(
    getPublicPath('home', 'en'),
    renderEnglishHomePage(
      template,
      assets,
      enHomeContent,
      enArticles,
      enArticlesById,
      enClusters,
    ),
  );
  writePublicPage(
    getPublicPath('about', 'en'),
    renderEnglishAboutPage(template, assets, enAboutContent),
  );
  writePublicPage(
    getPublicPath('articles', 'en'),
    renderEnglishArticlesIndex(template, assets, enClusters, enArticlesByCluster),
  );
  writePublicPage(
    getPublicPath('farmAutomation', 'en'),
    renderEnglishMiniFarmPage(template, assets, enMiniFarmContent),
  );
  writePublicPage(
    getPublicPath('gettingStarted', 'en'),
    renderEnglishGettingStartedPage(template, assets, enPlatformContent),
  );
  writePublicPage(
    getPublicPath('equipment', 'en'),
    renderEnglishEquipmentIndexPage(template, assets, enEquipment, enPlatformContent),
  );
  writePublicPage(
    getPublicPath('equipmentCoordinators', 'en'),
    renderEnglishEquipmentCategoryPage(template, assets, enEquipment, 'coordinators'),
  );
  writePublicPage(
    getPublicPath('equipmentSensors', 'en'),
    renderEnglishEquipmentCategoryPage(template, assets, enEquipment, 'sensors'),
  );
  writePublicPage(
    getPublicPath('equipmentSockets', 'en'),
    renderEnglishEquipmentCategoryPage(template, assets, enEquipment, 'sockets'),
  );
  writePublicPage(
    getPublicPath('equipmentPump', 'en'),
    renderEnglishPumpEarlyAccessPage(template, assets, enEquipment),
  );
  writePublicPage(
    getPublicPath('privacy', 'en'),
    renderEnglishLegalPage(template, assets, enLegalContent, 'privacy'),
  );
  writePublicPage(
    getPublicPath('terms', 'en'),
    renderEnglishLegalPage(template, assets, enLegalContent, 'terms'),
  );
  writeText(path.join(DIST_DIR, 'en', '404.html'), renderEnglish404(template, assets));

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

  for (const article of enArticles) {
    writePublicPage(
      getArticlePath(article, 'en'),
      renderEnglishArticlePage(
        template,
        assets,
        article,
        enArticlesById,
        enClustersById,
      ),
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

  for (const cluster of enClusters) {
    writePublicPage(
      getClusterPath(cluster, 'en'),
      renderEnglishClusterPage(
        template,
        assets,
        cluster,
        enArticlesByCluster.get(cluster.id) || [],
        enClusters.filter((item) => item.id !== cluster.id),
      ),
    );
  }

  const ruSitemapEntries = buildSitemapEntries({
    locale: 'ru',
    articles,
    clusters: articleClusters,
    articlesByCluster,
    homeContent,
    aboutContent,
    miniFarmContent,
    platformContent,
    equipment,
  });
  const enSitemapEntries = buildSitemapEntries({
    locale: 'en',
    articles: enArticles,
    clusters: enClusters,
    articlesByCluster: enArticlesByCluster,
    homeContent: enHomeContent,
    aboutContent: enAboutContent,
    miniFarmContent: enMiniFarmContent,
    platformContent: enPlatformContent,
    equipment: enEquipment,
  });
  writeSitemap([...ruSitemapEntries, ...enSitemapEntries]);
  writeRobots();
  console.log(
    `Generated static pages: ${articles.length + enArticles.length} articles, `
    + `${articleClusters.length + enClusters.length} clusters, 138 sitemap URLs`,
  );
};

main();
