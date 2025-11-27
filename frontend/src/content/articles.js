import matter from 'gray-matter';
import { marked } from 'marked';

// Sobiraem markdown stat'i i fron-matter v obychnye ob'ekty
const markdownModules = import.meta.glob('../../content/articles/*.md', { eager: true, as: 'raw' });

const parsedArticles = Object.values(markdownModules)
  .map((raw) => {
    const { data, content } = matter(raw);
    const body = content.trim();
    const bodyHtml = marked.parse(body);

    return {
      slug: data.slug,
      title: data.title,
      summary: data.summary,
      created_at: data.created_at,
      tags: data.tags || [],
      body,
      bodyHtml,
    };
  })
  .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));

export const articles = parsedArticles;

export const getArticleBySlug = (slug) =>
  articles.find((article) => article.slug === slug);
