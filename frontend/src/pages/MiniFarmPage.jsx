import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import PlatformStartLink from '../components/PlatformStartLink';
import TelegramContactLink from '../components/TelegramContactLink';
import { miniFarmContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { SELF_SERVICE_PUBLIC_ENABLED, SITE_URL } from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function MiniFarmPage() {
  const locale = getCurrentLocale();
  const path = getPublicPath('farmAutomation', locale);
  const data = miniFarmContent;
  const screenshotPrefix = locale === 'en' ? '/screenshots/en' : '/screenshots';
  const screenshotNames = ['zones', 'history', 'connection', 'automation'];
  const jsonLd = [{
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'GrowerHub',
    applicationCategory: 'BusinessApplication',
    operatingSystem: 'Web',
    url: `${SITE_URL}${path}`,
    description: data.description,
    inLanguage: locale,
    areaServed: locale === 'en' ? 'Russia and CIS countries' : 'Россия и страны СНГ',
    ...(SELF_SERVICE_PUBLIC_ENABLED ? {
      isAccessibleForFree: true,
      offers: { '@type': 'Offer', price: '0', priceCurrency: locale === 'en' ? 'USD' : 'RUB' },
    } : {}),
  }];

  useSeoMeta({
    title: data.title,
    description: data.description,
    path,
    jsonLd,
    locale,
  });

  return (
    <div className="section mini-farm-page">
      <section className="landing-hero">
        <div>
          <div className="badge">{data.hero.eyebrow}</div>
          <h1>{data.title}</h1>
          <p>{data.hero.text}</p>
          <div className="cta-row">
            <PlatformStartLink placement="mini_farm_hero">{SELF_SERVICE_PUBLIC_ENABLED ? data.hero.cta : translatePublic('Как начать')}</PlatformStartLink>
            <Link className="secondary-link" to={getPublicPath('equipment', locale)}>{translatePublic('Подобрать оборудование')}</Link>
          </div>
        </div>
        <aside className="landing-summary">
          <strong>{translatePublic('Ранний доступ открыт')}</strong>
          <p>{data.early_access}</p>
          <p>{translatePublic('Начните с оборудования и зон; растения и дополнительные настройки можно добавить позже.')}</p>
        </aside>
      </section>

      <section className="content-section">
        <h2>{translatePublic('Что можно сделать')}</h2>
        <div className="card-grid">
          {data.tasks.map((item) => <article className="card" key={item.title}><h3>{item.title}</h3><p>{item.text}</p></article>)}
        </div>
      </section>

      <section className="content-section split-section">
        <div><h2>{translatePublic('Возможности платформы')}</h2><ul className="check-list">{data.capabilities.map((item) => <li key={item}>{item}</li>)}</ul></div>
        <div className="info-block">
          <h2>{data.compatibility.title}</h2><p>{data.compatibility.text}</p>
          <p className="source-links"><a href="https://www.zigbee2mqtt.io/supported-devices/" target="_blank" rel="noreferrer">{translatePublic('Каталог Zigbee2MQTT')}</a> · <Link to={getPublicPath('equipment', locale)}>{translatePublic('Оборудование для старта')}</Link></p>
        </div>
      </section>

      <section className="content-section" id="demo-ekrany">
        <h2>{translatePublic('Настоящее приложение с виртуальной фермой')}</h2>
        <p>{translatePublic('Эти экраны сняты в демоферме GrowerHub. Устройства и показания симулируются, а разделы управления, графики и сценарии — те же, что в вашей ферме.')}</p>
        <div className="demo-grid demo-grid--four">
          {data.screens.map((screen, index) => (
            <figure className="demo-card" key={screen.title}>

                <img
                  className="product-screenshot"
                  src={`${screenshotPrefix}/${screenshotNames[index]}.webp`}
                  alt={screen.text}
                  width="1280"
                  height="720"
                  loading={index === 0 ? 'eager' : 'lazy'}
                />
              <figcaption><strong>{screen.title}</strong><span>{screen.text}</span></figcaption>
            </figure>
          ))}
        </div>
      </section>

      <section className="content-section">
        <h2>{translatePublic('Путь от входа до дашборда')}</h2>
        <ol className="steps-list">{data.stages.map((stage) => <li key={stage.title}><strong>{stage.title}</strong><span>{stage.text}</span></li>)}</ol>
      </section>

      <section className="content-section split-section">
        <div><h2>{translatePublic('Что важно знать')}</h2><ul className="check-list limitations-list">{data.limitations.map((item) => <li key={item}>{item}</li>)}</ul></div>
        <div className="info-block">
          <h2>{translatePublic('Мы рядом, если понадобится помощь')}</h2>
          <p>{translatePublic('Напишите нам в Telegram — поможем подключить оборудование, разобраться с функциями и настроить GrowerHub под вашу ферму на русском или английском.')}</p>
          <TelegramContactLink placement="mini_farm_help" className="secondary-link">{translatePublic('Помощь в Telegram')}</TelegramContactLink>
        </div>
      </section>

      <LeadCta placement="mini_farm_bottom" title={translatePublic('Подключите первое устройство')} text={translatePublic('Начните самостоятельно с координатора и датчика. Зоны и автоматизации можно добавлять постепенно.')} />
    </div>
  );
}

export default MiniFarmPage;
