import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import { equipmentContent, platformContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { SITE_NAME, SITE_URL } from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function EquipmentIndexPage() {
  const locale = getCurrentLocale();
  const description = translatePublic('equipment.description');
  const path = getPublicPath('equipment', locale);
  const categoryRouteIds = {
    coordinators: 'equipmentCoordinators',
    sensors: 'equipmentSensors',
    sockets: 'equipmentSockets',
  };
  const jsonLd = [{
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name: translatePublic('Оборудование для GrowerHub'),
    description,
    url: `${SITE_URL}${path}`,
    inLanguage: locale,
    isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: SITE_URL },
    mainEntity: {
      '@type': 'ItemList',
      itemListElement: Object.entries(equipmentContent.categories).map(([categoryKey, category], index) => ({
        '@type': 'ListItem',
        position: index + 1,
        name: category.title,
        url: `${SITE_URL}${getPublicPath(categoryRouteIds[categoryKey], locale)}`,
      })),
    },
  }];

  useSeoMeta({
    title: translatePublic('equipment.title'),
    description,
    path,
    jsonLd,
    locale,
  });

  return (
    <div className="section equipment-page">
      <div className="badge">{translatePublic('Без обязательных комплектов')}</div>
      <h1>{translatePublic('Оборудование для GrowerHub')}</h1>
      <p className="article-lead">{description}</p>

      <section className="content-section start-kit">
        <h2>{translatePublic('Минимальный старт')}</h2>
        <div className="card-grid">
          <article className="card"><h3>{translatePublic('Для мониторинга')}</h3><p>{platformContent.minimum.monitoring}</p></article>
          <article className="card"><h3>{translatePublic('Для управления')}</h3><p>{platformContent.minimum.control}</p></article>
          <article className="card"><h3>{translatePublic('Если всё уже работает')}</h3><p>{platformContent.minimum.existing}</p></article>
        </div>
      </section>

      <section className="content-section info-block">
        <h2>{translatePublic('Главное — выбрать Zigbee')}</h2>
        <p>{equipmentContent.zigbee_note}</p>
      </section>

      <section className="content-section">
        <h2>{translatePublic('Выберите раздел')}</h2>
        <div className="card-grid">
          {Object.entries(equipmentContent.categories).map(([categoryKey, category]) => (
            <article className="card" key={categoryKey}>
              <h3>{category.title}</h3><p>{category.intro}</p>
              <Link className="secondary-link" to={getPublicPath(categoryRouteIds[categoryKey], locale)}>
                {translatePublic('Посмотреть варианты')}
              </Link>
            </article>
          ))}
          <article className="card"><h3>{equipmentContent.pump.title}</h3><p>{equipmentContent.pump.summary}</p><Link className="secondary-link" to={getPublicPath('equipmentPump', locale)}>{translatePublic('О насосе GrowerHub')}</Link></article>
        </div>
      </section>

      <section className="content-section info-block">
        <h2>{translatePublic('Почему не любой Wi‑Fi-датчик')}</h2>
        <p>{equipmentContent.wifi_note}</p>
      </section>

      <LeadCta placement="equipment_index_bottom" />
    </div>
  );
}

export default EquipmentIndexPage;
