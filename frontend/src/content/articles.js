import { marked } from 'marked';

// Prostyj parser front matter bez Buffer/gray-matter (tol'ko dlya nashih md-fajlov)
const markdownModules = import.meta.glob('../../content/articles/*.md', { eager: true, as: 'raw' });

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
      // element massiva tags pri tekuschem kljuche
      if (currentKey) {
        result.meta[currentKey] = result.meta[currentKey] || [];
        result.meta[currentKey].push(trimmedLine.slice(2).trim());
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

const parsedArticles = Object.values(markdownModules)
  .map((raw) => {
    const { meta, body } = parseFrontMatter(raw);
    const bodyHtml = marked.parse(body || '');

    return {
      slug: meta.slug,
      title: meta.title,
      summary: meta.summary,
      created_at: meta.created_at,
      tags: meta.tags || [],
      body,
      bodyHtml,
    };
  })
  .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));

export const articles = parsedArticles;

export const getArticleBySlug = (slug) =>
  articles.find((article) => article.slug === slug);
