import { marked } from 'marked';
import { articleClusters } from './articleClusters';
import { canonicalizePublicLinksInHtml } from '../domain/siteConfig';

// Prostyj parser front matter bez Buffer/gray-matter (tol'ko dlya nashih md-fajlov)
const markdownModules = import.meta.glob('../../content/articles/*.md', {
  eager: true,
  query: '?raw',
  import: 'default',
});
const clusterSlugs = new Set(articleClusters.map((cluster) => cluster.slug));

const parseFrontMatter = (raw) => {
  const result = { meta: {}, body: '' };
  const trimmed = raw.trimStart();
  if (!trimmed.startsWith('---')) {
    result.body = raw;
    return result;
  }
  const end = trimmed.indexOf('\n---', 3);
  if (end === -1) {
    result.body = raw;
    return result;
  }
  const fmBlock = trimmed.slice(3, end).trim();
  const bodyStart = end + '\n---'.length;
  result.body = trimmed.slice(bodyStart).trim();

  const lines = fmBlock.split(/\r?\n/);
  let currentKey = null;
  for (const line of lines) {
    const trimmedLine = line.trim();
    if (!trimmedLine) continue;
    if (trimmedLine.startsWith('- ')) {
      // Element massiva pri tekuschem kljuche.
      if (currentKey) {
        result.meta[currentKey] = result.meta[currentKey] || [];
        result.meta[currentKey].push(trimmedLine.slice(2).trim().replace(/^"|"$/g, '').replace(/^'|'$/g, ''));
      }
      continue;
    }
    const [key, ...rest] = trimmedLine.split(':');
    const valueRaw = rest.join(':').trim();
    const value = valueRaw.replace(/^"|"$/g, '').replace(/^'|'$/g, '');
    currentKey = key.trim();
    if (value.startsWith('[') && value.endsWith(']')) {
      result.meta[currentKey] = value
        .slice(1, -1)
        .split(',')
        .map((v) => v.trim())
        .filter(Boolean);
      continue;
    }
    result.meta[currentKey] = value;
  }

  return result;
};

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

const parsedArticles = Object.values(markdownModules)
  .map((raw) => {
    const { meta, body } = parseFrontMatter(raw);
    const bodyHtml = canonicalizePublicLinksInHtml(marked.parse(body || ''));
    const cluster = clusterSlugs.has(meta.cluster) ? meta.cluster : '';

    return {
      slug: meta.slug,
      title: meta.title,
      summary: meta.summary,
      created_at: meta.created_at,
      updated_at: meta.updated_at || meta.created_at,
      cluster,
      tags: normalizeArray(meta.tags),
      keywords: normalizeArray(meta.keywords),
      related: normalizeArray(meta.related),
      hero_image: meta.hero_image || '',
      hero_alt: meta.hero_alt || meta.title || '',
      body,
      bodyHtml,
    };
  })
  .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));

export const articles = parsedArticles;

export const getArticleBySlug = (slug) =>
  articles.find((article) => article.slug === slug);

export const getArticlesByCluster = (clusterSlug) =>
  articles.filter((article) => article.cluster === clusterSlug);

export const getRelatedArticles = (article, limit = 3) => {
  if (!article) {
    return [];
  }

  const byFrontMatter = article.related
    .map((slug) => getArticleBySlug(slug))
    .filter(Boolean)
    .filter((relatedArticle) => relatedArticle.slug !== article.slug);

  if (byFrontMatter.length > 0) {
    return byFrontMatter.slice(0, limit);
  }

  return articles
    .filter((candidate) => candidate.slug !== article.slug && candidate.cluster === article.cluster)
    .slice(0, limit);
};
