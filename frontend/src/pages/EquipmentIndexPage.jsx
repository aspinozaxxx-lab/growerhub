import { useState } from 'react';
import { ArrowRight, Check, PackageCheck, Sprout, Truck, Wifi } from 'lucide-react';
import { Link } from 'react-router-dom';
import { NurseryFootnote, NurseryHelpCard, NurseryServiceStrip } from '../components/nursery/NurseryComponents';
import { getNurseryCopy, kitImages } from '../components/nursery/copy';
import { getPageContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { SITE_NAME, SITE_URL } from '../domain/siteConfig';
import { formatShopMoney, getOfferCopy, useShop } from '../features/shop';
import { getCurrentLocale } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function KitDetail({ offer, locale, currency }) {
  const { addItem, cartLocked } = useShop();
  const [added, setAdded] = useState(false);
  const copy = getNurseryCopy(locale);
  const product = getOfferCopy(offer.id, locale);
  const addToCart = () => {
    if (addItem(offer.id)) setAdded(true);
  };
  return <article id={offer.id} className="nursery-kit-detail" aria-labelledby={`title-${offer.id}`}>
    <figure className="nursery-kit-detail__media">
      <img src={kitImages[offer.id]} alt={copy.kitAlt[offer.id]} width="1000" height="750" loading="lazy" decoding="async" />
      <figcaption>{copy.illustration}</figcaption>
    </figure>
    <div>
      <span className="nursery-status">{copy.assembly}</span>
      <h2 id={`title-${offer.id}`}>{product.title}</h2>
      <p>{product.description}</p>
      <h3>{copy.contents}</h3>
      <ul>{product.contents.map((item) => <li key={item}>{item}</li>)}</ul>
      <div className={offer.id === 'light-mini' ? 'nursery-pilot' : ''}><p className="nursery-small">{product.verification}</p></div>
      <div className="nursery-purchase">
        <div><strong className="nursery-price">{formatShopMoney(offer.priceMinor, locale, currency)}</strong><p>{copy.priceNote}</p></div>
        <button type="button" className="nursery-button" onClick={addToCart} disabled={cartLocked} aria-label={`${copy.add}: ${product.title}`}>{copy.add}<ArrowRight aria-hidden="true" size={18} /></button>
      </div>
      <div className="nursery-added" role="status" aria-live="polite">{added && <><Check aria-hidden="true" size={18} /><span>{copy.added}</span><Link className="nursery-text-link" to={locale === 'en' ? '/en/cart/' : '/korzina/'}>{copy.cart}</Link></>}</div>
    </div>
  </article>;
}

function EquipmentIndexPage() {
  const locale = getCurrentLocale();
  const { equipmentContent, homeContent } = getPageContent(locale);
  const { catalog } = useShop();
  const copy = getNurseryCopy(locale);
  const content = copy.catalogue;
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
    {catalog.offers.map((offer) => <KitDetail key={offer.id} offer={offer} locale={locale} currency={catalog.currency} />)}
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
