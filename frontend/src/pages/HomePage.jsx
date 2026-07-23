import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import PlatformStartLink from '../components/PlatformStartLink';
import { articleClusters } from '../content/articleClusters';
import { articles, getArticleBySlug } from '../content/articles';
import { homeContent } from '../content/pages';
import { SELF_SERVICE_PUBLIC_ENABLED, SITE_URL } from '../domain/siteConfig';
import useSeoMeta from '../utils/useSeoMeta';

const pageDescription = 'GrowerHub объединяет Zigbee-устройства, зоны, историю датчиков и автоматизации мини-фермы в одном кабинете. Бесплатная открытая бета без карты.';

const softwareApplicationLd = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: 'GrowerHub',
  applicationCategory: 'BusinessApplication',
  operatingSystem: 'Web',
  url: SITE_URL,
  description: pageDescription,
  ...(SELF_SERVICE_PUBLIC_ENABLED ? {
    isAccessibleForFree: true,
    offers: { '@type': 'Offer', price: '0', priceCurrency: 'RUB' },
  } : {}),
};

function HomePage() {
  useSeoMeta({
    title: 'GrowerHub — платформа управления мини-фермой',
    description: pageDescription,
    path: '/',
    jsonLd: [softwareApplicationLd],
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
            <Link className="secondary-link" to="/kak-nachat/">Путь подключения</Link>
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

      <section className="content-section beta-note">
        <h2>Бесплатно в открытой бете</h2>
        <p>{homeContent.beta}</p>
        <div className="cta-row">
          <Link className="secondary-link" to="/oborudovanie/">Какое оборудование подойдёт</Link>
          <Link className="secondary-link" to="/avtomatizatsiya-mini-fermy/">Возможности платформы</Link>
        </div>
      </section>

      <section className="content-section">
        <h2>Практические разделы</h2>
        <div className="cluster-home-grid">
          {articleClusters.map((cluster) => {
            const featuredArticles = cluster.featuredArticles
              .map((slug) => getArticleBySlug(slug))
              .filter(Boolean)
              .slice(0, 4);

            return (
              <article className="article-card" key={cluster.slug}>
                <Link to={`/articles/clusters/${cluster.slug}/`}>{cluster.title}</Link>
                <p>{cluster.description}</p>
                <ul className="compact-link-list">
                  {featuredArticles.map((article) => (
                    <li key={article.slug}>
                      <Link to={`/articles/${article.slug}/`}>{article.title}</Link>
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
            <h2>Свежие статьи</h2>
            <p>Пошаговые материалы по Zigbee, Home Assistant, датчикам и безопасному поливу.</p>
          </div>
          <Link to="/articles/" className="secondary-link">Все статьи</Link>
        </div>
        <div className="articles-list">
          {recentArticles.map((article) => (
            <article className="article-card" key={article.slug}>
              <div className="article-meta">
                {new Date(article.updated_at).toLocaleDateString('ru-RU')}
              </div>
              <Link to={`/articles/${article.slug}/`}>{article.title}</Link>
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
