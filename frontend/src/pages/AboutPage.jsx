import LeadCta from '../components/LeadCta';
import { aboutContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { TELEGRAM_CHANNEL_URL } from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function AboutPage() {
  const locale = getCurrentLocale();
  useSeoMeta({
    title: translatePublic('about.title'),
    description: aboutContent.intro,
    path: getPublicPath('about', locale),
    locale,
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
        <h2>{translatePublic('Контакты')}</h2>
        <ul className="contact-list">
          <li><strong>{translatePublic('Сайт:')}</strong> <a href={aboutContent.contacts.site}>{aboutContent.contacts.site}</a></li>
          <li><strong>Telegram:</strong> <a href={TELEGRAM_CHANNEL_URL} target="_blank" rel="noreferrer">{translatePublic('канал GrowerHub')}</a></li>
        </ul>
      </section>
      <LeadCta placement="about_bottom" />
    </div>
  );
}

export default AboutPage;
