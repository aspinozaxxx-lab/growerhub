import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import PlatformStartLink from '../components/PlatformStartLink';
import DemoStartLink from '../components/DemoStartLink';
import { getArticleClusters } from '../content/articleClusters';
import { getArticles } from '../content/articles';
import { getPageContent } from '../content/pages';
import { overviewScreenshotDimensions, overviewScreenshotVersion } from '../content/productScreenshots';
import {
  getArticlePath,
  getClusterPath,
  getPublicPath,
} from '../domain/localizedRoutes';
import {
  GITHUB_REPOSITORY_URL,
  DEMO_PUBLIC_ENABLED,
  ORGANIZATION_ID,
  SELF_SERVICE_PUBLIC_ENABLED,
  SITE_NAME,
  SITE_URL,
  TELEGRAM_CHANNEL_URL,
  toCanonicalUrl,
} from '../domain/siteConfig';
import { getCurrentLocale, getIntlLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function HomePage() {
  const locale = getCurrentLocale();
  const { homeContent } = getPageContent(locale);
  const path = getPublicPath('home', locale);
  const pageDescription = homeContent.description;
  const organizationLd = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    '@id': ORGANIZATION_ID,
    name: SITE_NAME,
    url: SITE_URL,
    foundingDate: '2025-10-06',
    sameAs: [GITHUB_REPOSITORY_URL, TELEGRAM_CHANNEL_URL],
  };
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
    provider: { '@id': ORGANIZATION_ID },
    ...(SELF_SERVICE_PUBLIC_ENABLED ? {
      isAccessibleForFree: true,
      offers: { '@type': 'Offer', price: '0', priceCurrency: locale === 'en' ? 'USD' : 'RUB' },
    } : {}),
  };

  useSeoMeta({
    title: homeContent.title,
    description: pageDescription,
    path,
    jsonLd: [organizationLd, softwareApplicationLd],
    locale,
  });

  const { hero, secondary, features } = homeContent;
  const recentArticles = [...getArticles(locale)].sort((a, b) => new Date(b.updated_at) - new Date(a.updated_at) || a.slug.localeCompare(b.slug)).slice(0, 4);

  return (
    <div className="section home-page">
      <div className="hero">
        <div>
          <div className="badge">{hero.badge}</div>
          <h1>{hero.title}</h1>
          <p>{hero.subtitle}</p>
          <div className="cta-row">
            <DemoStartLink placement="home_hero_demo">{hero.demo_cta}</DemoStartLink>
            <PlatformStartLink placement="home_hero" className="secondary-link">
              {SELF_SERVICE_PUBLIC_ENABLED ? hero.cta : translatePublic('Как начать')}
            </PlatformStartLink>
          </div>
          {DEMO_PUBLIC_ENABLED && <p className="hero-reassurance">{hero.reassurance}</p>}
        </div>
        <figure className="hero-product-preview">
          <img src={`${hero.preview_image}?v=${overviewScreenshotVersion}`} srcSet={`${hero.preview_image.replace('.webp', '-640.webp')}?v=${overviewScreenshotVersion} 640w, ${hero.preview_image}?v=${overviewScreenshotVersion} 1280w`} sizes="(max-width: 800px) 100vw, 50vw" {...overviewScreenshotDimensions[locale]} fetchPriority="high" alt={hero.preview_alt} />
          <figcaption>{hero.preview_caption}</figcaption>
        </figure>
      </div>

      <section className="content-section">
        <h2>{features.title}</h2>
        <p>{features.text}</p>
        <div className="card-grid demo-task-grid">
          {features.items.map((item) => (
            <div className="card demo-task-card" key={item.title}>
              <h3>{item.title}</h3>
              <p>{item.text}</p>
              <DemoStartLink placement={`home_task_${item.view}`} view={item.view} className="secondary-link">{item.cta}</DemoStartLink>
            </div>
          ))}
        </div>
      </section>

      <section className="content-section">
        <h2>{secondary.title}</h2><p>{secondary.text}</p>
        <div className="card-grid">{secondary.points.map((point) => <div className="info-block" key={point.title}><strong>{point.title}</strong><p>{point.text}</p></div>)}</div>
        <div className="cta-row">
          <Link className="secondary-link" to={getPublicPath('gettingStarted', locale)}>{translatePublic('Путь подключения')}</Link>
          <Link className="secondary-link" to={getPublicPath('equipment', locale)}>
            {translatePublic('Какое оборудование подойдёт')}
          </Link>
        </div>
      </section>

      <section className="content-section">
        <h2>{homeContent.faq.title}</h2>
        <div className="home-faq">{homeContent.faq.items.map((item) => <details key={item.question}><summary>{item.question}</summary><p>{item.answer}</p></details>)}</div>
      </section>

      <section className="content-section info-block">
        <h2>{homeContent.evidence.title}</h2>
        <p>{homeContent.evidence.text}</p>
        <div className="cta-row">
          <Link className="secondary-link" to={getPublicPath('about', locale)}>{translatePublic('История и методика')}</Link>
          <a href={GITHUB_REPOSITORY_URL} target="_blank" rel="noreferrer">
            {translatePublic('Исходный код на GitHub')}
          </a>
        </div>
      </section>

      <section className="content-section">
        <h2>{translatePublic('Практические разделы')}</h2>
        <div className="cluster-home-grid">
          {getArticleClusters(locale).map((cluster) => (
              <article className="article-card" key={cluster.slug}>
                <Link to={getClusterPath(cluster, locale)}>{cluster.title}</Link>
                <p>{cluster.description}</p>
              </article>
          ))}
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
                {translatePublic('Обновлено')} {new Date(article.updated_at).toLocaleDateString(getIntlLocale(locale), { timeZone: 'UTC' })}
              </div>
              <Link to={getArticlePath(article, locale)}>{article.title}</Link>
              <p>{article.summary}</p>
            </article>
          ))}
        </div>
      </section>

      <LeadCta placement="home_bottom" title={homeContent.cta.title} text={homeContent.cta.text} />
    </div>
  );
}

export default HomePage;
