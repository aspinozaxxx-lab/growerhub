import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import matter from 'gray-matter';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const RU_DIR = path.join(ROOT, 'content', 'articles');
const EN_DIR = path.join(ROOT, 'content', 'en', 'articles');
const OUTPUT_PATH = path.join(ROOT, 'src', 'content', 'articleMetadata.generated.json');

const normalizeArray = (value) => {
  if (Array.isArray(value)) return value.map(String).map((item) => item.trim()).filter(Boolean);
  if (!value) return [];
  return String(value).split(',').map((item) => item.trim()).filter(Boolean);
};

const readLocale = (directory, locale) => {
  if (!fs.existsSync(directory)) {
    throw new Error(`Article directory is missing: ${directory}`);
  }

  return fs.readdirSync(directory)
    .filter((name) => name.endsWith('.md'))
    .sort()
    .map((fileName) => {
      const raw = fs.readFileSync(path.join(directory, fileName), 'utf8').replace(/^\ufeff/, '');
      const parsed = matter(raw);
      const id = locale === 'ru' ? parsed.data.slug : parsed.data.translation_of;

      if (!id || !parsed.data.slug || !parsed.data.title || !parsed.data.summary) {
        throw new Error(`Required article metadata is missing: ${locale}/${fileName}`);
      }
      if (parsed.data.product_cta !== undefined && typeof parsed.data.product_cta !== 'boolean') {
        throw new Error(`product_cta must be a boolean: ${locale}/${fileName}`);
      }

      return {
        id,
        slug: parsed.data.slug,
        title: parsed.data.title,
        summary: parsed.data.summary,
        created_at: parsed.data.created_at,
        updated_at: parsed.data.updated_at || parsed.data.created_at,
        cluster: parsed.data.cluster || '',
        related: normalizeArray(parsed.data.related),
        hero_image: parsed.data.hero_image || '',
        hero_alt: parsed.data.hero_alt || parsed.data.title,
        download: parsed.data.download || null,
        product_cta: parsed.data.product_cta ?? true,
        source_file: fileName,
        hero_in_body: Boolean(
          parsed.data.hero_image && parsed.content.includes(`](${parsed.data.hero_image})`),
        ),
      };
    });
};

const ru = readLocale(RU_DIR, 'ru');
const en = readLocale(EN_DIR, 'en');

if (!ru.length || ru.length !== en.length) {
  throw new Error(`Article locales must be nonempty and have equal size, got ru=${ru.length}, en=${en.length}`);
}

const ruIds = new Set(ru.map((article) => article.id));
const enIds = new Set(en.map((article) => article.id));
const missingInEnglish = [...ruIds].filter((id) => !enIds.has(id));
const missingInRussian = [...enIds].filter((id) => !ruIds.has(id));

if (missingInEnglish.length || missingInRussian.length) {
  throw new Error(
    `Article translation mismatch; missing en=[${missingInEnglish.join(', ')}], missing ru=[${missingInRussian.join(', ')}]`,
  );
}

for (const localeArticles of [ru, en]) {
  const slugs = localeArticles.map((article) => article.slug);
  if (new Set(slugs).size !== slugs.length) {
    throw new Error(`Duplicate ${localeArticles[0]?.locale || 'unknown'} article slugs`);
  }
}

const validDate = (value) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
};
const newsByLocale = {};
for (const [locale, localeArticles] of [['ru', ru], ['en', en]]) {
  const newsPath = path.join(ROOT, 'content', ...(locale === 'en' ? ['en'] : []), 'pages', 'news.json');
  const news = JSON.parse(fs.readFileSync(newsPath, 'utf8').replace(/^\ufeff/, ''));
  const articleById = new Map(localeArticles.map((article) => [article.id, article]));
  const ids = new Set();
  if (!news.entries?.length) throw new Error(`News must be nonempty: ${locale}`);
  for (const entry of news.entries) {
    if (!entry.id || ids.has(entry.id) || !validDate(entry.published_at)) {
      throw new Error(`Invalid news ID or date: ${locale}/${entry.id}`);
    }
    ids.add(entry.id);
    const major = entry.kind === 'feature' || entry.kind === 'article';
    if (!['feature', 'article', 'fix', 'article_update'].includes(entry.kind) || !news.labels?.[entry.kind]) {
      throw new Error(`Invalid news kind: ${locale}/${entry.id}`);
    }
    const article = entry.article_id ? articleById.get(entry.article_id) : null;
    if ((entry.article_id && !article) || (major && !article) || (entry.kind === 'article_update' && !article)) {
      throw new Error(`Missing news article: ${locale}/${entry.id}`);
    }
    if (!major && !entry.text?.trim()) throw new Error(`Missing news text: ${locale}/${entry.id}`);
    if (major && (!article.hero_image?.startsWith('/') || !article.hero_alt
      || !fs.existsSync(path.join(ROOT, 'public', article.hero_image.slice(1))))) {
      throw new Error(`Missing news image: ${locale}/${entry.id}`);
    }
    if (article && (!validDate(article.created_at) || !validDate(article.updated_at)
      || article.created_at > article.updated_at || article.updated_at < entry.published_at)) {
      throw new Error(`Invalid news article dates: ${locale}/${entry.id}`);
    }
  }
  newsByLocale[locale] = new Map(news.entries.map((entry) => [entry.id, entry]));
}
if (newsByLocale.ru.size !== newsByLocale.en.size || [...newsByLocale.ru].some(([id, entry]) => {
  const translation = newsByLocale.en.get(id);
  return !translation || translation.kind !== entry.kind || translation.published_at !== entry.published_at
    || translation.article_id !== entry.article_id;
})) {
  throw new Error('News translation mismatch');
}

const ruById = new Map(ru.map((article) => [article.id, article]));
const withSortDates = (articles) => articles
  .sort((left, right) => (
    new Date(ruById.get(right.id)?.created_at || right.created_at)
      - new Date(ruById.get(left.id)?.created_at || left.created_at)
    || left.id.localeCompare(right.id)
  ));

fs.writeFileSync(
  OUTPUT_PATH,
  `${JSON.stringify({
    fields: [
      'id',
      'slug',
      'title',
      'summary',
      'created_at',
      'updated_at',
      'cluster',
      'related',
      'hero_image',
      'hero_alt',
      'source_file',
      'hero_in_body',
      'download',
      'product_cta',
    ],
    ru: withSortDates(ru).map((article) => [
      article.id,
      article.slug,
      article.title,
      article.summary,
      article.created_at,
      article.updated_at,
      article.cluster,
      article.related,
      article.hero_image,
      article.hero_alt,
      article.source_file,
      article.hero_in_body,
      article.download,
      article.product_cta,
    ]),
    en: withSortDates(en).map((article) => [
      article.id,
      article.slug,
      article.title,
      article.summary,
      article.created_at,
      article.updated_at,
      article.cluster,
      article.related,
      article.hero_image,
      article.hero_alt,
      article.source_file,
      article.hero_in_body,
      article.download,
      article.product_cta,
    ]),
  })}\n`,
  'utf8',
);

console.log(`Article metadata generated: ${ru.length} ru + ${en.length} en`);
