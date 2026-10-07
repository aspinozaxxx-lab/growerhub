import { useEffect, useRef } from 'react';
import { Lightbulb, Sprout, Wifi } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import DemoStartLink from '../components/DemoStartLink';
import NetworkCoordinatorPilot from '../components/NetworkCoordinatorPilot';
import HubPhotos from '../components/HubPhotos';
import { getNurseryCopy } from '../components/nursery/copy';
import '../components/nursery/nursery.css';
import { getPageContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { DEMO_PUBLIC_ENABLED, SITE_NAME, SITE_URL } from '../domain/siteConfig';
import { getCurrentLocale, rememberLocale } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';
import { useAuth } from '../features/auth/AuthContext';
import { useShop } from '../features/shop';

function GettingStartedPage() {
  const locale = getCurrentLocale();
  const { platformContent } = getPageContent(locale);
  const { demoActive, leaveDemo } = useAuth();
  const { openConsultation } = useShop();
  const { hash } = useLocation();
  const technical = useRef(null);
  const content = getNurseryCopy(locale).start;
  const path = getPublicPath('gettingStarted', locale);
  const { start, minimum, pushok, networkCoordinator } = platformContent;
  const pilotTarget = `/app/login/?lang=${locale}&redirect=${encodeURIComponent(`/app/settings/connections/?pilot=pushok&lang=${locale}`)}`;
  useEffect(() => {
    if ((hash === '#pushok' || hash === '#zs-eht') && technical.current) technical.current.open = true;
  }, [hash]);
  useSeoMeta({
    title: `${content.title} — GrowerHub`, description: content.description, path, locale,
    jsonLd: [{
      '@context': 'https://schema.org', '@type': 'HowTo', name: content.title,
      description: content.description, url: `${SITE_URL}${path}`, inLanguage: locale,
      publisher: { '@type': 'Organization', name: SITE_NAME, url: SITE_URL },
      step: content.steps.map((step, index) => ({ '@type': 'HowToStep', position: index + 1, name: step.title, text: step.text })),
    }],
  });

  return <div className="nursery-page nursery-start">
    <header className="nursery-page-heading"><p className="nursery-eyebrow"><Sprout aria-hidden="true" size={19} />{content.eyebrow}</p><h1>{content.title}</h1><p>{content.description}</p></header>
    <div className="nursery-start-paths">
      {DEMO_PUBLIC_ENABLED && <section className="nursery-start-choice"><Sprout aria-hidden="true" size={35} /><h2>{content.demoTitle}</h2><p>{content.demoText}</p><DemoStartLink placement="getting_started_hero_demo" className="nursery-button nursery-button--outline">{content.demoCta}</DemoStartLink></section>}
      <section className="nursery-start-choice"><Lightbulb aria-hidden="true" size={35} /><h2>{content.kitTitle}</h2><p>{content.kitText}</p><Link className="nursery-button" to={getPublicPath('equipment', locale)}>{content.kitCta}</Link></section>
    </div>
    <section className="nursery-section" aria-labelledby="nursery-setup-title"><h2 id="nursery-setup-title">{content.stepsTitle}</h2><p>{content.stepsIntro}</p>
      <ol className="nursery-setup-steps">{content.steps.map((step) => <li key={step.title}><h3>{step.title}</h3><p>{step.text}</p></li>)}</ol>
    </section>
    <section className="nursery-info-panel"><Wifi aria-hidden="true" size={28} /><h2>{content.connectionTitle}</h2><p>{content.connectionText}</p></section>
    <section className="nursery-help-row" id="help"><div><h2>{content.helpTitle}</h2><p>{content.helpText}</p></div><button className="nursery-button" type="button" onClick={openConsultation}>{content.helpCta}</button></section>

    <details className="nursery-technical" ref={technical} open={hash === '#pushok' || hash === '#zs-eht' ? true : undefined}>
      <summary>{content.technical}</summary>
      <div className="nursery-technical__content">
        <p>{content.technicalIntro}</p>
        <section className="content-section" id="pushok">
          <div className="badge">{pushok.status}</div><h2>{pushok.title}</h2><HubPhotos type="pushok" />
          <p>{pushok.intro}</p><p><strong>{pushok.availability}</strong></p>
          <div className="nursery-actions"><Link className="nursery-button" to={pilotTarget} onClick={() => { rememberLocale(locale); if (demoActive) leaveDemo(); }}>{pushok.cta}</Link></div>
          <h3>{pushok.path_title}</h3>
          <ol className="steps-list">{pushok.steps.map((step) => <li key={step.title}><strong>{step.title}</strong><span>{step.text}</span></li>)}</ol>
          <p>{pushok.invitation}</p><p className="nursery-small">{pushok.purchase_note}</p>
          <div className="nursery-actions">{pushok.links.map((link) => <a key={link.href} className="nursery-text-link" href={link.href} target={link.href.startsWith('/') ? undefined : '_blank'} rel={link.href.startsWith('/') ? undefined : 'noreferrer'}>{link.label}</a>)}</div>
        </section>
        <section className="content-section" id="zs-eht">
          <div className="badge">{networkCoordinator.status}</div><h2>{networkCoordinator.title}</h2><HubPhotos type="networkCoordinator" />
          <p>{networkCoordinator.intro}</p><p><strong>{networkCoordinator.requirement}</strong></p><p>{networkCoordinator.path}</p><p>{networkCoordinator.purchase_note}</p>
          <div className="nursery-actions"><NetworkCoordinatorPilot placement="getting_started_zs_eht_pilot" /></div>
          <div className="nursery-actions">{networkCoordinator.links.map((link) => <a key={link.href} className="nursery-text-link" href={link.href}>{link.label}</a>)}</div>
        </section>
        <section className="content-section"><h2>{start.steps_title}</h2><ol className="steps-list">{start.steps.map((step) => <li key={step.title}><strong>{step.title}</strong><span>{step.text}</span></li>)}</ol></section>
        <section className="nursery-info-grid">
          <div className="nursery-info-panel"><h2>{minimum.title}</h2><h3>{content.monitoring}</h3><p>{minimum.monitoring}</p><h3>{content.control}</h3><p>{minimum.control}</p></div>
          <div className="nursery-info-panel"><h2>{content.existing}</h2><p>{minimum.existing}</p><Link className="nursery-text-link" to={getPublicPath('equipmentCoordinators', locale)}>{content.equipment}</Link></div>
        </section>
        <section className="nursery-section"><h2>{content.morePaths}</h2><div className="nursery-category-grid">{start.paths.filter((item) => !item.demo || DEMO_PUBLIC_ENABLED).map((item) => <article className="nursery-category" key={item.href}><h3>{item.href.includes('#') ? <a href={item.href}>{item.title}</a> : <Link to={item.href}>{item.title}</Link>}</h3><p>{item.text}</p></article>)}</div></section>
      </div>
    </details>
  </div>;
}

export default GettingStartedPage;
