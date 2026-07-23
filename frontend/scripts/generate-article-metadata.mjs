import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import matter from 'gray-matter';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const RU_DIR = path.join(ROOT, 'content', 'articles');
const EN_DIR = path.join(ROOT, 'content', 'en', 'articles');
const OUTPUT_PATH = path.join(ROOT, 'src', 'content', 'articleMetadata.generated.json');
const EXPECTED_ARTICLES = 54;

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
        source_file: fileName,
        hero_in_body: Boolean(
          parsed.data.hero_image && parsed.content.includes(`](${parsed.data.hero_image})`),
        ),
      };
    });
};

const ru = readLocale(RU_DIR, 'ru');
const en = readLocale(EN_DIR, 'en');

if (ru.length !== EXPECTED_ARTICLES || en.length !== EXPECTED_ARTICLES) {
  throw new Error(`Expected ${EXPECTED_ARTICLES} articles per locale, got ru=${ru.length}, en=${en.length}`);
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
    ]),
  })}\n`,
  'utf8',
);

console.log(`Article metadata generated: ${ru.length} ru + ${en.length} en`);
