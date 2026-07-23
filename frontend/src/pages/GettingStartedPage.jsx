import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import TelegramContactLink from '../components/TelegramContactLink';
import { platformContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { SITE_NAME, SITE_URL } from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function GettingStartedPage() {
  const locale = getCurrentLocale();
  const path = getPublicPath('gettingStarted', locale);
  const { start, minimum, early_access_text: earlyAccessText } = platformContent;
  const jsonLd = [{
    '@context': 'https://schema.org',
    '@type': 'HowTo',
    name: start.title,
    description: start.description,
    url: `${SITE_URL}${path}`,
    inLanguage: locale,
    publisher: { '@type': 'Organization', name: SITE_NAME, url: SITE_URL },
    step: start.steps.map((step, index) => ({
      '@type': 'HowToStep',
      position: index + 1,
      name: step.title,
      text: step.text,
    })),
  }];

  useSeoMeta({
    title: `${start.title} — Zigbee2MQTT`,
    description: start.description,
    path,
    jsonLd,
    locale,
  });

  return (
    <div className="section equipment-page">
      <section className="landing-hero">
        <div>
          <div className="badge">{translatePublic('Самостоятельный запуск')}</div>
          <h1>{start.title}</h1>
          <p>{start.intro}</p>
          <LeadCta placement="getting_started_hero" compact />
        </div>
        <aside className="landing-summary">
          <strong>{translatePublic('Ранний доступ открыт')}</strong>
          <p>{earlyAccessText}</p>
        </aside>
      </section>

      <section className="content-section">
        <h2>{translatePublic('Семь коротких шагов')}</h2>
        <ol className="steps-list">
          {start.steps.map((step) => <li key={step.title}><strong>{step.title}</strong><span>{step.text}</span></li>)}
        </ol>
      </section>

      <section className="content-section split-section">
        <div>
          <h2>{minimum.title}</h2>
          <div className="info-grid">
            <div className="info-block"><h3>{translatePublic('Только мониторинг')}</h3><p>{minimum.monitoring}</p></div>
            <div className="info-block"><h3>{translatePublic('Управление')}</h3><p>{minimum.control}</p></div>
          </div>
        </div>
        <div className="info-block">
          <h2>{translatePublic('Уже есть Home Assistant?')}</h2>
          <p>{minimum.existing}</p>
          <Link className="secondary-link" to={getPublicPath('equipmentCoordinators', locale)}>
            {translatePublic('Проверить оборудование')}
          </Link>
        </div>
      </section>

      <section className="content-section">
        <h2>{translatePublic('Поможем с подключением и настройкой')}</h2>
        <p>{translatePublic('Если что-то не подключается или хочется быстрее разобраться с функцией, напишите нам в Telegram. Команда GrowerHub поможет на любом этапе на русском или английском.')}</p>
        <TelegramContactLink placement="getting_started_help" className="secondary-link">{translatePublic('Помощь в Telegram')}</TelegramContactLink>
      </section>

      <LeadCta placement="getting_started_bottom" />
    </div>
  );
}

export default GettingStartedPage;
