import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import { equipmentContent, platformContent } from '../content/pages';
import { SITE_NAME, SITE_URL } from '../domain/siteConfig';
import useSeoMeta from '../utils/useSeoMeta';

function EquipmentIndexPage() {
  const categories = Object.values(equipmentContent.categories);
  const description = 'Что нужно для GrowerHub: Zigbee-координатор, один датчик температуры и влажности, а для управления — Zigbee-розетка.';
  const jsonLd = [{
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name: 'Оборудование для GrowerHub',
    description,
    url: `${SITE_URL}/oborudovanie/`,
    isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: SITE_URL },
    mainEntity: {
      '@type': 'ItemList',
      itemListElement: categories.map((category, index) => ({
        '@type': 'ListItem',
        position: index + 1,
        name: category.title,
        url: `${SITE_URL}/oborudovanie/${category.slug}/`,
      })),
    },
  }];

  useSeoMeta({ title: 'Оборудование для GrowerHub — мягкие рекомендации', description, path: '/oborudovanie/', jsonLd });

  return (
    <div className="section equipment-page">
      <div className="badge">Без обязательных комплектов</div>
      <h1>Оборудование для GrowerHub</h1>
      <p className="article-lead">{description}</p>

      <section className="content-section start-kit">
        <h2>Минимальный старт</h2>
        <div className="card-grid">
          <article className="card"><h3>Для мониторинга</h3><p>{platformContent.minimum.monitoring}</p></article>
          <article className="card"><h3>Для управления</h3><p>{platformContent.minimum.control}</p></article>
          <article className="card"><h3>Если всё уже работает</h3><p>{platformContent.minimum.existing}</p></article>
        </div>
      </section>

      <section className="content-section">
        <h2>Выберите раздел</h2>
        <div className="card-grid">
          {categories.map((category) => (
            <article className="card" key={category.slug}>
              <h3>{category.title}</h3><p>{category.intro}</p>
              <Link className="secondary-link" to={`/oborudovanie/${category.slug}/`}>Посмотреть варианты</Link>
            </article>
          ))}
          <article className="card"><h3>{equipmentContent.pump.title}</h3><p>{equipmentContent.pump.summary}</p><Link className="secondary-link" to="/oborudovanie/nasos-dlya-poliva/">О раннем доступе</Link></article>
        </div>
      </section>

      <section className="content-section info-block">
        <h2>Почему не любой Wi‑Fi-датчик</h2>
        <p>{equipmentContent.wifi_note}</p>
      </section>

      <LeadCta placement="equipment_index_bottom" />
    </div>
  );
}

export default EquipmentIndexPage;
