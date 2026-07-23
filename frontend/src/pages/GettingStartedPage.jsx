import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import TelegramContactLink from '../components/TelegramContactLink';
import { platformContent } from '../content/pages';
import { SITE_NAME, SITE_URL } from '../domain/siteConfig';
import useSeoMeta from '../utils/useSeoMeta';

function GettingStartedPage() {
  const { start, minimum, early_access_text: earlyAccessText } = platformContent;
  const jsonLd = [{
    '@context': 'https://schema.org',
    '@type': 'HowTo',
    name: start.title,
    description: start.description,
    url: `${SITE_URL}/kak-nachat/`,
    publisher: { '@type': 'Organization', name: SITE_NAME, url: SITE_URL },
    step: start.steps.map((step, index) => ({
      '@type': 'HowToStep',
      position: index + 1,
      name: step.title,
      text: step.text,
    })),
  }];

  useSeoMeta({
    title: `${start.title} — подключение Zigbee2MQTT`,
    description: start.description,
    path: '/kak-nachat/',
    jsonLd,
  });

  return (
    <div className="section equipment-page">
      <section className="landing-hero">
        <div>
          <div className="badge">Самостоятельный запуск</div>
          <h1>{start.title}</h1>
          <p>{start.intro}</p>
          <LeadCta placement="getting_started_hero" compact />
        </div>
        <aside className="landing-summary">
          <strong>Ранний доступ открыт</strong>
          <p>{earlyAccessText}</p>
        </aside>
      </section>

      <section className="content-section">
        <h2>Семь коротких шагов</h2>
        <ol className="steps-list">
          {start.steps.map((step) => <li key={step.title}><strong>{step.title}</strong><span>{step.text}</span></li>)}
        </ol>
      </section>

      <section className="content-section split-section">
        <div>
          <h2>{minimum.title}</h2>
          <div className="info-grid">
            <div className="info-block"><h3>Только мониторинг</h3><p>{minimum.monitoring}</p></div>
            <div className="info-block"><h3>Управление</h3><p>{minimum.control}</p></div>
          </div>
        </div>
        <div className="info-block">
          <h2>Уже есть Home Assistant?</h2>
          <p>{minimum.existing}</p>
          <Link className="secondary-link" to="/oborudovanie/zigbee-koordinator/">Проверить оборудование</Link>
        </div>
      </section>

      <section className="content-section">
        <h2>Поможем с подключением и настройкой</h2>
        <p>Если что-то не подключается или хочется быстрее разобраться с функцией, напишите нам в Telegram. Команда GrowerHub поможет на любом этапе.</p>
        <TelegramContactLink placement="getting_started_help" className="secondary-link">Помощь в Telegram</TelegramContactLink>
      </section>

      <LeadCta placement="getting_started_bottom" />
    </div>
  );
}

export default GettingStartedPage;
