import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import { equipmentContent } from '../content/pages';
import { SITE_NAME, SITE_URL } from '../domain/siteConfig';
import useSeoMeta from '../utils/useSeoMeta';

function EquipmentCategoryPage({ categoryKey }) {
  const category = equipmentContent.categories[categoryKey];
  const path = `/oborudovanie/${category.slug}/`;
  const jsonLd = [{
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name: category.title,
    description: category.description,
    url: `${SITE_URL}${path}`,
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

  useSeoMeta({ title: `${category.title} — GrowerHub`, description: category.description, path, jsonLd });

  return (
    <div className="section equipment-page">
      <Link className="secondary-link" to="/oborudovanie/">← Всё оборудование</Link>
      <div className="badge">Проверено {new Date(equipmentContent.checked_at).toLocaleDateString('ru-RU')}</div>
      <h1>{category.title}</h1>
      <p className="article-lead">{category.intro}</p>
      <p className="equipment-disclaimer">{equipmentContent.purchase_note}</p>

      <div className="equipment-list content-section">
        {category.items.map((item) => (
          <article className="equipment-card" key={item.model}>
            <div><span className="status-chip">{item.status}</span><h2>{item.model}</h2><h3>{item.name}</h3><p>{item.summary}</p></div>
            <ul className="check-list">{item.notes.map((note) => <li key={note}>{note}</li>)}</ul>
            <div className="cta-row">
              <a className="secondary-link" href={item.official_url} target="_blank" rel="noreferrer">Совместимость Zigbee2MQTT</a>
              {item.example_url ? <a className="secondary-link" href={item.example_url} target="_blank" rel="nofollow noreferrer">Пример на Ozon</a> : null}
              <a className="secondary-link" href={item.shop_search_url} target="_blank" rel="nofollow noreferrer">Найти на Ozon</a>
            </div>
          </article>
        ))}
      </div>

      {categoryKey === 'sensors' ? <section className="content-section info-block"><h2>Wi‑Fi-датчики</h2><p>{equipmentContent.wifi_note}</p></section> : null}
      <LeadCta placement={`equipment_${category.slug}_bottom`} />
    </div>
  );
}

export default EquipmentCategoryPage;
