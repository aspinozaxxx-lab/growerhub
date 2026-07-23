import LeadCta from '../components/LeadCta';
import { aboutContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import {
  GITHUB_REPOSITORY_URL,
  ORGANIZATION_ID,
  SITE_NAME,
  SITE_URL,
  TELEGRAM_CHANNEL_URL,
  toCanonicalUrl,
} from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function AboutPage() {
  const locale = getCurrentLocale();
  const path = getPublicPath('about', locale);
  const organizationLd = {
    '@type': 'Organization',
    '@id': ORGANIZATION_ID,
    name: SITE_NAME,
    url: SITE_URL,
    foundingDate: '2025-10-06',
    sameAs: [GITHUB_REPOSITORY_URL, TELEGRAM_CHANNEL_URL],
  };

  useSeoMeta({
    title: `${aboutContent.title} — GrowerHub`,
    description: aboutContent.intro,
    path,
    locale,
    jsonLd: [{
      '@context': 'https://schema.org',
      '@type': 'AboutPage',
      name: aboutContent.title,
      description: aboutContent.intro,
      url: toCanonicalUrl(path),
      dateModified: aboutContent.updated_at,
      isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: SITE_URL },
      mainEntity: organizationLd,
    }],
  });

  return (
    <div className="section">
      <h1>{aboutContent.title}</h1>
      <p>{aboutContent.intro}</p>
      <div className="info-grid content-section">
        <div className="info-block">
          <h2>{translatePublic('Задача проекта')}</h2>
          <p>{aboutContent.mission}</p>
        </div>
        <div className="info-block">
          <h2>{translatePublic('Чем помогаем')}</h2>
          <p>{aboutContent.value}</p>
        </div>
      </div>

      <section className="content-section">
        <h2>{aboutContent.evidence.title}</h2>
        <p>{aboutContent.evidence.intro}</p>
        <div className="card-grid">
          {aboutContent.evidence.facts.map((fact) => (
            <article className="card" key={fact.label}>
              <h3>{fact.value}</h3>
              <strong>{fact.label}</strong>
              <p>{fact.detail}</p>
            </article>
          ))}
        </div>
        <div className="info-block content-section">
          <h3>{aboutContent.evidence.methodology_title}</h3>
          <p>{aboutContent.evidence.methodology}</p>
        </div>
      </section>

      <section className="content-section">
        <h2>{aboutContent.timeline.title}</h2>
        <ol className="steps-list">
          {aboutContent.timeline.items.map((item) => (
            <li key={`${item.date}-${item.title}`}>
              <time>{item.date}</time>
              <strong>{item.title}</strong>
              <span>{item.text}</span>
              <div className="source-links">
                {item.links.map((link, index) => (
                  <span key={link.url}>
                    {index > 0 ? ' · ' : ''}
                    <a href={link.url} target="_blank" rel="noreferrer">
                      {link.label}
                    </a>
                  </span>
                ))}
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="content-section info-block">
        <h2>{aboutContent.experience.title}</h2>
        <ul className="check-list">
          {aboutContent.experience.points.map((point) => <li key={point}>{point}</li>)}
        </ul>
      </section>

      <section className="content-section">
        <h2>{translatePublic('Контакты')}</h2>
        <ul className="contact-list">
          <li><strong>{translatePublic('Сайт:')}</strong> <a href={aboutContent.contacts.site}>{aboutContent.contacts.site}</a></li>
          <li>
            <strong>GitHub:</strong>{' '}
            <a href={aboutContent.contacts.github} target="_blank" rel="noreferrer">
              {aboutContent.contacts.github_label}
            </a>
          </li>
          <li><strong>Telegram:</strong> <a href={TELEGRAM_CHANNEL_URL} target="_blank" rel="noreferrer">{translatePublic('канал GrowerHub')}</a></li>
        </ul>
      </section>
      <LeadCta placement="about_bottom" />
    </div>
  );
}

export default AboutPage;
