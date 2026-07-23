import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import PlatformStartLink from '../components/PlatformStartLink';
import { articleClusters } from '../content/articleClusters';
import { articles, getArticleById } from '../content/articles';
import { homeContent } from '../content/pages';
import {
  getArticlePath,
  getClusterPath,
  getPublicPath,
} from '../domain/localizedRoutes';
import { SELF_SERVICE_PUBLIC_ENABLED, toCanonicalUrl } from '../domain/siteConfig';
import { getCurrentLocale, getIntlLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function HomePage() {
  const locale = getCurrentLocale();
  const path = getPublicPath('home', locale);
  const pageDescription = translatePublic('home.description');
  const softwareApplicationLd = {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'GrowerHub',
    applicationCategory: 'BusinessApplication',
    operatingSystem: 'Web',
    url: toCanonicalUrl(path),
    description: pageDescription,
    inLanguage: locale,
    areaServed: locale === 'en' ? 'Russia and CIS countries' : 'Россия и страны СНГ',
    ...(SELF_SERVICE_PUBLIC_ENABLED ? {
      isAccessibleForFree: true,
      offers: { '@type': 'Offer', price: '0', priceCurrency: locale === 'en' ? 'USD' : 'RUB' },
    } : {}),
  };

  useSeoMeta({
    title: translatePublic('home.title'),
    description: pageDescription,
    path,
    jsonLd: [softwareApplicationLd],
    locale,
  });

  const { hero, secondary, features } = homeContent;
  const recentArticles = articles.slice(0, 4);

  return (
    <div className="section">
      <div className="hero">
        <div>
          <div className="badge">{hero.badge}</div>
          <h1>{hero.title}</h1>
          <p>{hero.subtitle}</p>
          <div className="cta-row">
            <PlatformStartLink placement="home_hero">
              {SELF_SERVICE_PUBLIC_ENABLED ? hero.cta : 'Как начать'}
            </PlatformStartLink>
            <Link className="secondary-link" to={getPublicPath('gettingStarted', locale)}>
              {translatePublic('Путь подключения')}
            </Link>
          </div>
        </div>
        <div className="card">
          <h2>{secondary.title}</h2>
          <p>{secondary.text}</p>
          <div className="card-grid">
            {secondary.points.map((point) => (
              <div key={point.title} className="info-block">
                <strong>{point.title}</strong>
                <p>{point.text}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      <section className="content-section">
        <h2>{features.title}</h2>
        <div className="card-grid">
          {features.items.map((item) => (
            <div className="card" key={item.title}>
              <h3>{item.title}</h3>
              <p>{item.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="content-section early-access-note">
        <h2>{translatePublic('Большой опыт автоматизации — в одной платформе')}</h2>
        <p>{homeContent.early_access}</p>
        <div className="cta-row">
          <Link className="secondary-link" to={getPublicPath('equipment', locale)}>
            {translatePublic('Какое оборудование подойдёт')}
          </Link>
          <Link className="secondary-link" to={getPublicPath('farmAutomation', locale)}>
            {translatePublic('Возможности платформы')}
          </Link>
        </div>
      </section>

      <section className="content-section">
        <h2>{translatePublic('Практические разделы')}</h2>
        <div className="cluster-home-grid">
          {articleClusters.map((cluster) => {
            const featuredArticles = cluster.featuredArticles
              .map((id) => getArticleById(id, locale))
              .filter(Boolean)
              .slice(0, 4);

            return (
              <article className="article-card" key={cluster.slug}>
                <Link to={getClusterPath(cluster, locale)}>{cluster.title}</Link>
                <p>{cluster.description}</p>
                <ul className="compact-link-list">
                  {featuredArticles.map((article) => (
                    <li key={article.slug}>
                      <Link to={getArticlePath(article, locale)}>{article.title}</Link>
                    </li>
                  ))}
                </ul>
              </article>
            );
          })}
        </div>
      </section>

      <section className="content-section">
        <div className="cluster-block__header">
          <div>
            <h2>{translatePublic('Свежие статьи')}</h2>
            <p>{translatePublic('Пошаговые материалы по Zigbee, Home Assistant, датчикам и безопасному поливу.')}</p>
          </div>
          <Link to={getPublicPath('articles', locale)} className="secondary-link">
            {translatePublic('Все статьи')}
          </Link>
        </div>
        <div className="articles-list">
          {recentArticles.map((article) => (
            <article className="article-card" key={article.slug}>
              <div className="article-meta">
                {new Date(article.updated_at).toLocaleDateString(getIntlLocale(locale))}
              </div>
              <Link to={getArticlePath(article, locale)}>{article.title}</Link>
              <p>{article.summary}</p>
            </article>
          ))}
        </div>
      </section>

      <LeadCta placement="home_bottom" />
    </div>
  );
}

export default HomePage;
