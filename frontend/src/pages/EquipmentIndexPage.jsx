import { ArrowRight, PackageCheck, Sprout, Truck, Wifi } from 'lucide-react';
import { Link } from 'react-router-dom';
import { NurseryFootnote, NurseryHelpCard, NurseryProductDetail, NurseryServiceStrip } from '../components/nursery/NurseryComponents';
import { getNurseryCopy } from '../components/nursery/copy';
import { getPageContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { SITE_NAME, SITE_URL } from '../domain/siteConfig';
import { getOfferCopy, useShop } from '../features/shop';
import { getCurrentLocale } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function EquipmentIndexPage() {
  const locale = getCurrentLocale();
  const { equipmentContent, homeContent } = getPageContent(locale);
  const { catalog } = useShop();
  const copy = getNurseryCopy(locale);
  const content = copy.catalogue;
  const hubOffers = catalog.offers.filter((offer) => offer.id.startsWith('light-'));
  const soilOffer = catalog.offers.find((offer) => offer.id === 'soil-sensor');
  const pumpOffers = ['pump', 'pump-drip-kit'].map((id) => catalog.offers.find((offer) => offer.id === id)).filter(Boolean);
  const path = getPublicPath('equipment', locale);
  const categoryRouteIds = { coordinators: 'equipmentCoordinators', sensors: 'equipmentSensors', sockets: 'equipmentSockets' };
  useSeoMeta({
    title: `${content.title} — GrowerHub`, description: content.description, path, locale,
    image: '/content/nursery/kit-three.webp',
    jsonLd: [{
      '@context': 'https://schema.org', '@type': 'CollectionPage', name: content.title,
      description: content.description, url: `${SITE_URL}${path}`, inLanguage: locale,
      isPartOf: { '@type': 'WebSite', name: SITE_NAME, url: SITE_URL },
      mainEntity: {
        '@type': 'ItemList', itemListElement: catalog.offers.map((offer, index) => ({
          '@type': 'ListItem', position: index + 1, name: getOfferCopy(offer.id, locale).title, url: `${SITE_URL}${path}#${offer.id}`,
        })),
      },
    }],
  });

  return <div className="nursery-page nursery-catalogue">
    <header className="nursery-page-heading"><p className="nursery-eyebrow"><Sprout aria-hidden="true" size={19} />{content.eyebrow}</p><h1>{content.title}</h1><p>{content.description}</p></header>
    <NurseryServiceStrip items={homeContent.service} />
    <section aria-labelledby="kit-comparison-title"><h2 id="kit-comparison-title">{content.compare}</h2><p>{content.compareIntro}</p>
      <div className="nursery-compare">{content.comparing.map((item) => <a key={item.id} href={`#${item.id}`}><h3>{item.title}</h3><p>{item.text}</p><ArrowRight aria-hidden="true" size={20} /></a>)}</div>
    </section>
    <p><a className="nursery-text-link" href="#watering-products">{content.additionsLink}<ArrowRight aria-hidden="true" size={18} /></a></p>
    {hubOffers.map((offer) => <NurseryProductDetail key={offer.id} offers={[offer]} locale={locale} currency={catalog.currency} />)}
    <section id="watering-products" className="nursery-section" aria-labelledby="watering-products-title">
      <h2 id="watering-products-title">{content.additionsTitle}</h2><p>{content.additionsIntro}</p>
      {soilOffer && <NurseryProductDetail offers={[soilOffer]} locale={locale} currency={catalog.currency} />}
      {pumpOffers.length > 0 && <NurseryProductDetail offers={pumpOffers} locale={locale} currency={catalog.currency} />}
    </section>
    <NurseryFootnote>{homeContent.kits.lamps_note}</NurseryFootnote>
    <div className="nursery-info-grid">
      <section className="nursery-info-panel"><PackageCheck aria-hidden="true" size={30} /><h2>{content.includedTitle}</h2><p>{content.includedText}</p></section>
      <section className="nursery-info-panel"><Truck aria-hidden="true" size={30} /><h2>{content.deliveryTitle}</h2><p>{content.deliveryText}</p><a className="nursery-text-link" href="https://www.cdek.ru/ru/offices/" target="_blank" rel="noreferrer">{locale === 'en' ? 'Find a CDEK pickup point' : 'Найти пункт СДЭК'}<ArrowRight aria-hidden="true" size={17} /></a></section>
    </div>
    <div className="nursery-catalogue-help">
      <div className="nursery-checks">
        <section className="nursery-info-panel"><Wifi aria-hidden="true" size={29} /><h2>{content.limitsTitle}</h2><ul>{content.limits.map((item) => <li key={item}>{item}</li>)}</ul></section>
        <section className="nursery-info-panel"><h2>{content.checkedTitle}</h2><p>{content.checkedText}</p></section>
      </div>
      <NurseryHelpCard content={homeContent.help} />
    </div>
    <details className="nursery-technical">
      <summary>{content.technical}</summary>
      <div className="nursery-technical__content"><p>{content.technicalIntro}</p>
        <div className="nursery-category-grid">{Object.entries(equipmentContent.categories).map(([key, category]) => <article className="nursery-category" key={key}><h3>{category.title}</h3><p>{category.intro}</p><Link className="nursery-text-link" to={getPublicPath(categoryRouteIds[key], locale)}>{content.categoryLink}</Link></article>)}
          <article className="nursery-category"><h3>{equipmentContent.pump.title}</h3><p>{equipmentContent.pump.summary}</p><Link className="nursery-text-link" to={getPublicPath('equipmentPump', locale)}>{content.pumpLink}</Link></article>
        </div>
        <p className="nursery-small">{content.compatibility}</p>
      </div>
    </details>
  </div>;
}

export default EquipmentIndexPage;
