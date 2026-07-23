import metadata from './articleMetadata.generated.json';
import { getArticleClusters } from './articleClusters';
import {
  DEFAULT_LOCALE,
  PUBLIC_ROUTES,
  getArticlePath,
  normalizeLocale,
} from '../domain/localizedRoutes';
import { canonicalizePublicLinksInHtml } from '../domain/siteConfig';
import { getCurrentLocale } from '../locales/i18n';

const markdownModules = {
  ru: import.meta.glob('../../content/articles/*.md', {
    query: '?raw',
    import: 'default',
  }),
  en: import.meta.glob('../../content/en/articles/*.md', {
    query: '?raw',
    import: 'default',
  }),
};

const expandMetadata = (rows, locale) => rows.map((row) => ({
  ...Object.fromEntries(metadata.fields.map((field, index) => [field, row[index]])),
  locale,
}));

const metadataByLocale = Object.freeze({
  ru: expandMetadata(metadata.ru, 'ru'),
  en: expandMetadata(metadata.en, 'en'),
});

const articlesById = {
  ru: new Map(metadataByLocale.ru.map((article) => [article.id, article])),
  en: new Map(metadataByLocale.en.map((article) => [article.id, article])),
};

const articlesBySlug = {
  ru: new Map(metadataByLocale.ru.map((article) => [article.slug, article])),
  en: new Map(metadataByLocale.en.map((article) => [article.slug, article])),
};

const stripFrontMatter = (raw = '') => {
  const normalized = String(raw).replace(/^\ufeff/, '').trimStart();
  if (!normalized.startsWith('---')) return normalized;
  const end = normalized.indexOf('\n---', 3);
  return end < 0 ? normalized : normalized.slice(end + 4).trim();
};

const resolveMarkdownImporter = (article) => {
  const modules = markdownModules[article.locale] || markdownModules.ru;
  const suffix = `/${article.source_file}`.replaceAll('\\', '/');
  const entry = Object.entries(modules).find(([modulePath]) => (
    modulePath.replaceAll('\\', '/').endsWith(suffix)
  ));
  return entry?.[1] || null;
};

const localizeInternalLinks = (html, locale) => {
  const normalizedLocale = normalizeLocale(locale);
  if (normalizedLocale === DEFAULT_LOCALE) {
    return canonicalizePublicLinksInHtml(html);
  }

  const ruArticles = articlesBySlug.ru;
  let localized = String(html).replace(
    /href="\/articles\/([^"?#/]+)\/?([?#][^"]*)?"/g,
    (match, slug, suffix = '') => {
      const source = ruArticles.get(slug);
      const translated = source ? articlesById.en.get(source.id) : null;
      return translated
        ? `href="${getArticlePath(translated, 'en')}${suffix}"`
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

export const getArticles = (locale = getCurrentLocale()) => (
  metadataByLocale[normalizeLocale(locale)] || metadataByLocale.ru
);

export const articles = getArticles();

export const getArticleBySlug = (slug, locale = getCurrentLocale()) => (
  articlesBySlug[normalizeLocale(locale)].get(slug)
);

export const getArticleById = (id, locale = getCurrentLocale()) => (
  articlesById[normalizeLocale(locale)].get(id)
);

export const getArticleTranslation = (article, locale) => (
  article ? getArticleById(article.id, locale) : null
);

export const getArticlesByCluster = (clusterIdOrSlug, locale = getCurrentLocale()) => {
  const normalizedLocale = normalizeLocale(locale);
  const cluster = getArticleClusters(normalizedLocale).find((item) => (
    item.id === clusterIdOrSlug || item.slug === clusterIdOrSlug
  ));
  if (!cluster) return [];
  return getArticles(normalizedLocale).filter((article) => article.cluster === cluster.id);
};

export const getRelatedArticles = (article, limit = 3, locale = getCurrentLocale()) => {
  if (!article) return [];
  const normalizedLocale = normalizeLocale(locale);

  const byFrontMatter = article.related
    .map((idOrSlug) => (
      getArticleById(idOrSlug, normalizedLocale)
      || getArticleBySlug(idOrSlug, normalizedLocale)
      || getArticleTranslation(articlesBySlug.ru.get(idOrSlug), normalizedLocale)
    ))
    .filter(Boolean)
    .filter((relatedArticle) => relatedArticle.id !== article.id);

  if (byFrontMatter.length > 0) {
    return byFrontMatter.slice(0, limit);
  }

  return getArticles(normalizedLocale)
    .filter((candidate) => candidate.id !== article.id && candidate.cluster === article.cluster)
    .slice(0, limit);
};

export const loadArticleBody = async (article) => {
  if (!article) return '';
  const importer = resolveMarkdownImporter(article);
  if (!importer) {
    throw new Error(`Markdown module is missing: ${article.locale}/${article.source_file}`);
  }

  const [raw, markedModule] = await Promise.all([
    importer(),
    import('marked'),
  ]);
  const body = stripFrontMatter(raw);
  return localizeInternalLinks(markedModule.marked.parse(body), article.locale);
};
