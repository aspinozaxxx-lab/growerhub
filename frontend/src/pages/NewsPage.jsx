import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import { getArticleById } from '../content/articles';
import { getPageContent } from '../content/pages';
import { getArticlePath, getPublicPath } from '../domain/localizedRoutes';
import { SITE_URL } from '../domain/siteConfig';
import { getCurrentLocale, getIntlLocale } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';
import './NewsPage.css';

function NewsPage() {
  const locale = getCurrentLocale();
  const { newsContent: content } = getPageContent(locale);
  const path = getPublicPath('news', locale);
  const entries = [...content.entries].sort((left, right) => (
    right.published_at.localeCompare(left.published_at)
  ));
  const dates = [...new Set(entries.map((entry) => entry.published_at))];
  const articleEntries = entries.filter((entry) => (
    entry.kind === 'feature' || entry.kind === 'article'
  ));

  useSeoMeta({
    title: content.title,
    description: content.description,
    path,
    locale,
    jsonLd: [{
      '@context': 'https://schema.org',
      '@type': 'CollectionPage',
      name: content.title,
      description: content.description,
      url: `${SITE_URL}${path}`,
      inLanguage: locale,
      mainEntity: {
        '@type': 'ItemList',
        itemListElement: articleEntries.map((entry, index) => ({
          '@type': 'ListItem',
          position: index + 1,
          url: `${SITE_URL}${getArticlePath(getArticleById(entry.article_id, locale), locale)}`,
        })),
      },
    }],
  });

  return (
    <div className="section news-page">
      <header className="news-intro">
        <h1>{content.heading}</h1>
        <p>{content.description}</p>
      </header>
      {dates.map((date) => {
        const dayEntries = entries.filter((entry) => entry.published_at === date);
        const major = dayEntries.filter((entry) => entry.kind === 'feature' || entry.kind === 'article');
        const minor = dayEntries.filter((entry) => entry.kind === 'fix' || entry.kind === 'article_update');
        return (
          <section className="news-release" key={date} aria-labelledby={`release-${date}`}>
            <h2 id={`release-${date}`} className="news-date">
              <time dateTime={date}>
                {new Date(`${date}T00:00:00Z`).toLocaleDateString(getIntlLocale(locale), {
                  day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC',
                })}
              </time>
            </h2>
            {major.length > 0 && (
              <div className="news-grid">
                {major.map((entry) => {
                  const article = getArticleById(entry.article_id, locale);
                  return (
                    <article className="news-card" key={entry.id} data-news-id={entry.id}>
                      <Link className="news-card__image" to={getArticlePath(article, locale)} tabIndex={-1} aria-hidden="true">
                        <img src={article.hero_image} alt="" loading="lazy" width="960" height="640"
                          style={{ objectPosition: entry.preview_position || '50% 22%' }} />
                      </Link>
                      <div className="news-card__body">
                        <span className="news-kind">{content.labels[entry.kind]}</span>
                        <h3><Link to={getArticlePath(article, locale)}>{article.title}</Link></h3>
                        <p>{article.summary}</p>
                        <Link className="secondary-link" to={getArticlePath(article, locale)}>{content.read_more}</Link>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
            {minor.length > 0 && (
              <div className="news-small">
                <h3>{content.small_heading}</h3>
                <ul>
                  {minor.map((entry) => {
                    const article = entry.article_id ? getArticleById(entry.article_id, locale) : null;
                    return (
                      <li key={entry.id} data-news-id={entry.id}>
                        <span className="news-small__kind">{content.labels[entry.kind]}</span>
                        <span>{entry.text}{article && <> <Link to={getArticlePath(article, locale)}>{content.open_article}</Link></>}</span>
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}
          </section>
        );
      })}
      <LeadCta placement="news_bottom" />
    </div>
  );
}

export default NewsPage;
