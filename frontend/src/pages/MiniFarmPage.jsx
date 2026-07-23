import { Link, useLocation } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import PlatformStartLink from '../components/PlatformStartLink';
import TelegramContactLink from '../components/TelegramContactLink';
import { miniFarmContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { SELF_SERVICE_PUBLIC_ENABLED, SITE_URL } from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function ZonesDemo() {
  return (
    <div className="product-demo" role="img" aria-label={translatePublic('Синтетический обзор двух зон GrowerHub')}>
      <div className="product-demo__bar"><span /> GrowerHub · {translatePublic('зоны')}</div>
      <div className="product-demo__zones">
        <div><strong>{translatePublic('Стеллаж «Зелень»')}</strong><span className="status-ok">{translatePublic('В сети')}</span><p>{translatePublic('23,4 °C · 61% · свет включён')}</p></div>
        <div><strong>{translatePublic('Бокс «Рассада»')}</strong><span className="status-warn">{translatePublic('Проверить')}</span><p>{translatePublic('24,1 °C · 57% · данные 12 мин назад')}</p></div>
      </div>
    </div>
  );
}

function HistoryDemo() {
  return (
    <div className="product-demo" role="img" aria-label={translatePublic('Синтетическая история датчиков GrowerHub')}>
      <div className="product-demo__bar"><span /> {translatePublic('История · последние 24 часа')}</div>
      <svg className="demo-chart" viewBox="0 0 520 180" aria-hidden="true">
        <path className="demo-chart__grid" d="M20 30H500M20 75H500M20 120H500M20 165H500" />
        <path className="demo-chart__temperature" d="M20 102 C70 88 92 96 136 72 S220 92 268 62 S350 76 400 52 S456 70 500 42" />
        <path className="demo-chart__humidity" d="M20 55 C68 68 100 52 142 78 S222 70 270 98 S354 82 402 112 S466 98 500 124" />
      </svg>
      <div className="product-demo__legend"><span>{translatePublic('Температура')}</span><span>{translatePublic('Влажность воздуха')}</span></div>
    </div>
  );
}

function ConnectionDemo() {
  return (
    <div className="product-demo" role="img" aria-label={translatePublic('Синтетический экран подключения GrowerHub')}>
      <div className="product-demo__bar"><span /> {translatePublic('Подключения')}</div>
      <div className="product-demo__connection">
        <div><strong>{translatePublic('Координатор «Теплица»')}</strong><span className="status-ok">{translatePublic('В сети')}</span></div>
        <p>{translatePublic('3 устройства обнаружены автоматически')}</p>
        <ul><li>{translatePublic('Датчик микроклимата')}</li><li>{translatePublic('Розетка освещения')}</li><li>{translatePublic('Датчик протечки')}</li></ul>
      </div>
    </div>
  );
}

function AutomationDemo() {
  return (
    <div className="product-demo" role="img" aria-label={translatePublic('Синтетический экран автоматизации GrowerHub')}>
      <div className="product-demo__bar"><span /> {translatePublic('Автоматизации · зона «Рассада»')}</div>
      <dl className="product-demo__rules">
        <div><dt>{translatePublic('Освещение')}</dt><dd>{translatePublic('06:00–22:00 · готово')}</dd></div>
        <div><dt>{translatePublic('Климат')}</dt><dd>{translatePublic('24–28 °C · гистерезис включён')}</dd></div>
        <div><dt>{translatePublic('Полив')}</dt><dd>{translatePublic('готов к настройке после назначения датчика и насоса')}</dd></div>
      </dl>
      <span className="status-ok">{translatePublic('1 сценарий активен')}</span>
    </div>
  );
}

const demoViews = [
  <ZonesDemo key="zones" />,
  <HistoryDemo key="history" />,
  <ConnectionDemo key="connection" />,
  <AutomationDemo key="automation" />,
];

function MiniFarmPage() {
  const location = useLocation();
  const locale = getCurrentLocale();
  const path = getPublicPath('farmAutomation', locale);
  const data = miniFarmContent;
  const screenshotPrefix = locale === 'en' ? '/screenshots/en' : '/screenshots';
  const screenshotImages = [
    { src: `${screenshotPrefix}/zones.png`, alt: translatePublic('Обзор двух зон GrowerHub на синтетических данных') },
    { src: `${screenshotPrefix}/history.png`, alt: translatePublic('История температуры и влажности GrowerHub на синтетических данных') },
    { src: `${screenshotPrefix}/connection.png`, alt: translatePublic('Подключение Zigbee-координатора GrowerHub на синтетических данных') },
    { src: `${screenshotPrefix}/automation.png`, alt: translatePublic('Автоматизации GrowerHub на синтетических данных') },
  ];
  const captureMode = new URLSearchParams(location.search).get('capture') === 'screenshots';
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
        <h2>{translatePublic('Интерфейс на синтетических данных')}</h2>
        <p>{translatePublic('Все названия и значения вымышлены. На экранах нет реальных адресов, IEEE, логинов или данных доступа.')}</p>
        <div className="demo-grid demo-grid--four">
          {data.screens.map((screen, index) => (
            <figure className="demo-card" key={screen.title}>
              {captureMode ? demoViews[index] : (
                <img
                  className="product-screenshot"
                  src={screenshotImages[index].src}
                  alt={screenshotImages[index].alt}
                  width="1010"
                  height="520"
                  loading={index === 0 ? 'eager' : 'lazy'}
                />
              )}
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
