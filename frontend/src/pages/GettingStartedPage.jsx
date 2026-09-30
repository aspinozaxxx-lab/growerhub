import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import DemoStartLink from '../components/DemoStartLink';
import PlatformStartLink from '../components/PlatformStartLink';
import TelegramContactLink from '../components/TelegramContactLink';
import NetworkCoordinatorPilot from '../components/NetworkCoordinatorPilot';
import HubPhotos from '../components/HubPhotos';
import { getPageContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { DEMO_PUBLIC_ENABLED, SITE_NAME, SITE_URL } from '../domain/siteConfig';
import { getCurrentLocale, rememberLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';
import { useAuth } from '../features/auth/AuthContext';

function GettingStartedPage() {
  const locale = getCurrentLocale();
  const { platformContent } = getPageContent(locale);
  const { demoActive, leaveDemo } = useAuth();
  const path = getPublicPath('gettingStarted', locale);
  const { start, minimum, pushok, networkCoordinator, early_access_text: earlyAccessText } = platformContent;
  const pilotTarget = `/app/login/?lang=${locale}&redirect=${encodeURIComponent(`/app/settings/connections/?pilot=pushok&lang=${locale}`)}`;
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
    title: `${start.title} — Zigbee2MQTT, PushOk`,
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
          <div className="cta-row">
            <DemoStartLink placement="getting_started_hero_demo" />
            <PlatformStartLink placement="getting_started_hero" className="secondary-link" />
          </div>
        </div>
        <aside className="landing-summary">
          <strong>{translatePublic('Ранний доступ открыт')}</strong>
          <p>{earlyAccessText}</p>
        </aside>
      </section>

      <section className="content-section">
        <h2>{start.paths_title}</h2>
        <div className="card-grid">{start.paths.filter((item) => !item.demo || DEMO_PUBLIC_ENABLED).map((item) => <article className="card" key={item.href}>
          <h3>{item.href.includes('#') ? <a href={item.href}>{item.title}</a> : <Link to={item.href}>{item.title}</Link>}</h3>
          <p>{item.text}</p>
        </article>)}</div>
      </section>

      <section className="content-section info-block" id="pushok">
        <div className="badge">{pushok.status}</div>
        <h2>{pushok.title}</h2>
        <HubPhotos type="pushok" />
        <p>{pushok.intro}</p>
        <p><strong>{pushok.availability}</strong></p>
        <div className="cta-row">
          <Link className="hero-cta" to={pilotTarget} onClick={() => { rememberLocale(locale); if (demoActive) leaveDemo(); }}>{pushok.cta}</Link>
        </div>
        <h3>{pushok.path_title}</h3>
        <ol className="steps-list">{pushok.steps.map((step) => <li key={step.title}><strong>{step.title}</strong><span>{step.text}</span></li>)}</ol>
        <p>{pushok.invitation}</p>
        <p className="equipment-disclaimer">{pushok.purchase_note}</p>
        <div className="cta-row">{pushok.links.map((link) => <a key={link.href} className="secondary-link" href={link.href} target="_blank" rel="noreferrer">{link.label}</a>)}</div>
      </section>

      <section className="content-section info-block" id="zs-eht">
        <div className="badge">{networkCoordinator.status}</div>
        <h2>{networkCoordinator.title}</h2>
        <HubPhotos type="networkCoordinator" />
        <p>{networkCoordinator.intro}</p>
        <p><strong>{networkCoordinator.requirement}</strong></p>
        <p>{networkCoordinator.path}</p>
        <p>{networkCoordinator.purchase_note}</p>
        <div className="cta-row"><NetworkCoordinatorPilot placement="getting_started_zs_eht_pilot" /></div>
        <div className="cta-row">{networkCoordinator.links.map((link) => <a key={link.href} className="secondary-link" href={link.href}>{link.label}</a>)}</div>
      </section>

      <section className="content-section">
        <h2>{start.steps_title}</h2>
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
        <p>{start.help}</p>
        <TelegramContactLink placement="getting_started_help" className="secondary-link">{translatePublic('Помощь в Telegram')}</TelegramContactLink>
      </section>

      <LeadCta placement="getting_started_bottom" />
    </div>
  );
}

export default GettingStartedPage;
