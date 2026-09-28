import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import { getPageContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { SITE_NAME, SITE_URL } from '../domain/siteConfig';
import { getCurrentLocale, getIntlLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function EquipmentCategoryPage({ categoryKey }) {
  const locale = getCurrentLocale();
  const { equipmentContent, platformContent } = getPageContent(locale);
  const category = equipmentContent.categories[categoryKey];
  const routeIds = {
    coordinators: 'equipmentCoordinators',
    sensors: 'equipmentSensors',
    sockets: 'equipmentSockets',
  };
  const path = getPublicPath(routeIds[categoryKey], locale);
  const jsonLd = [{
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name: category.title,
    description: category.description,
    url: `${SITE_URL}${path}`,
    inLanguage: locale,
    isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: SITE_URL },
    mainEntity: {
      '@type': 'ItemList',
      itemListElement: category.items.map((item, index) => ({
        '@type': 'ListItem',
        position: index + 1,
        name: item.model,
        url: item.official_url,
      })),
    },
  }];

  useSeoMeta({
    title: `${category.title} — GrowerHub`,
    description: category.description,
    path,
    jsonLd,
    locale,
  });

  return (
    <div className="section equipment-page">
      <Link className="secondary-link" to={getPublicPath('equipment', locale)}>
        ← {translatePublic('Всё оборудование')}
      </Link>
      <div className="badge">
        {translatePublic('Проверено')} {new Date(equipmentContent.checked_at).toLocaleDateString(getIntlLocale(locale))}
      </div>
      <h1>{category.title}</h1>
      <p className="article-lead">{category.intro}</p>
      <p className="equipment-disclaimer">{equipmentContent.purchase_note}</p>
      {categoryKey === 'coordinators' ? <section className="content-section info-block">
        <h2>{platformContent.pushok.title}</h2>
        <p>{platformContent.pushok.availability}</p>
        <a className="secondary-link" href={`${getPublicPath('gettingStarted', locale)}#pushok`}>{platformContent.pushok.cta}</a>
      </section> : null}
      {categoryKey === 'coordinators' ? <section className="content-section info-block">
        <h2>{platformContent.networkCoordinator.title}</h2>
        <p>{platformContent.networkCoordinator.requirement}</p>
        <a className="secondary-link" href={`${getPublicPath('gettingStarted', locale)}#zs-eht`}>{platformContent.networkCoordinator.button}</a>
      </section> : null}
      <section className="content-section info-block">
        <h2>{translatePublic('Как читать наши рекомендации')}</h2>
        <p>{translatePublic('В карточках отдельно указано, что работало в установке GrowerHub, а что рекомендовано по официальной совместимости Zigbee2MQTT. Проверка не является гарантией для всех white-label ревизий одной модели.')}</p>
        <Link className="secondary-link" to={getPublicPath('about', locale)}>
          {translatePublic('Эксплуатационный срез и методика')}
        </Link>
      </section>

      <div className="equipment-list content-section">
        {category.items.map((item) => (
          <article className="equipment-card" key={item.model}>
            {item.image ? (
              <figure className="equipment-card__media">
                <img
                  src={item.image}
                  alt={item.image_alt || item.model}
                  width="640"
                  height="420"
                  loading="lazy"
                  decoding="async"
                />
                {item.image_caption ? <figcaption>{item.image_caption}</figcaption> : null}
              </figure>
            ) : null}
            <div><span className="status-chip">{item.status}</span><h2>{item.model}</h2><h3>{item.name}</h3><p>{item.summary}</p></div>
            <ul className="check-list">{item.notes.map((note) => <li key={note}>{note}</li>)}</ul>
            <div className="cta-row">
              <a className="secondary-link" href={item.official_url} target="_blank" rel="noreferrer">{item.official_label || translatePublic('Совместимость Zigbee2MQTT')}</a>
              {(locale === 'ru' || item.example_label) && item.example_url ? <a className="secondary-link" href={item.example_url} target="_blank" rel="nofollow noreferrer">{item.example_label || translatePublic('Пример на Ozon')}</a> : null}
              <a className="secondary-link" href={item.shop_search_url} target="_blank" rel="nofollow noreferrer">
                {item.shop_label || translatePublic(locale === 'ru' ? 'Найти на Ozon' : 'Найти модель')}
              </a>
            </div>
          </article>
        ))}
      </div>

      {categoryKey === 'sensors' ? <section className="content-section info-block"><h2>{translatePublic('Wi‑Fi-датчики')}</h2><p>{equipmentContent.wifi_note}</p></section> : null}
      <LeadCta placement={`equipment_${category.slug}_bottom`} />
    </div>
  );
}

export default EquipmentCategoryPage;
