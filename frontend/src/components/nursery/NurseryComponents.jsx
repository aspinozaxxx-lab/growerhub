import { useState, useSyncExternalStore } from 'react';
import { ArrowRight, BadgeCheck, Check, Heart, Info, Leaf, Sprout, Truck, Wifi } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import DemoStartLink from '../DemoStartLink';
import { getPublicPath } from '../../domain/localizedRoutes';
import { DEMO_PUBLIC_ENABLED } from '../../domain/siteConfig';
import { formatShopMoney, getOfferCopy, useShop } from '../../features/shop';
import { getNurseryCopy, kitImages } from './copy';
import './nursery.css';

const subscribeToHash = () => () => {};
const serverHash = () => '';

export function NurseryNote({ children, className = '' }) {
  return <span className={`nursery-note ${className}`}>{children}<Heart aria-hidden="true" size={23} strokeWidth={1.5} /></span>;
}

export function NurseryServiceStrip({ items }) {
  const icons = [BadgeCheck, Truck, Sprout];
  return <ul className="nursery-service-strip">{items.map((item, index) => {
    const Icon = icons[index] || Leaf;
    return <li key={item.title}><Icon aria-hidden="true" size={29} strokeWidth={1.6} /><div><strong>{item.title}</strong><span>{item.text}</span></div></li>;
  })}</ul>;
}

export function NurseryKitCard({ offer, locale }) {
  const copy = getNurseryCopy(locale);
  const product = getOfferCopy(offer.id, locale);
  return <article className="nursery-kit-card" aria-labelledby={`kit-card-${offer.id}`}>
    <div className="nursery-kit-card__copy">
      <h3 id={`kit-card-${offer.id}`}>{product.title}</h3>
      <p>{product.description}</p>
    </div>
    <img src={kitImages[offer.id]} alt={copy.kitAlt[offer.id]} width="720" height="540" loading="lazy" decoding="async" />
    <div className="nursery-kit-card__footer">
      <strong className="nursery-price">{formatShopMoney(offer.priceMinor, locale)}</strong>
      <Link className="nursery-button" to={`${getPublicPath('equipment', locale)}#${offer.id}`} aria-label={`${copy.details}: ${product.title}`}>
        {copy.details}<ArrowRight aria-hidden="true" size={19} />
      </Link>
    </div>
  </article>;
}

export function NurseryProductDetail({ offers, locale, currency }) {
  const { addItem, cartLocked } = useShop();
  const { hash } = useLocation();
  const variantHash = useSyncExternalStore(subscribeToHash, () => hash, serverHash);
  const [selection, setSelection] = useState(null);
  const [addedId, setAddedId] = useState(null);
  const selectedId = selection?.hash === variantHash ? selection.id : variantHash.slice(1);
  const offer = offers.find((item) => item.id === selectedId) || offers[0];
  const copy = getNurseryCopy(locale);
  const product = getOfferCopy(offer.id, locale);
  const groupId = offers[0].id;
  const addToCart = () => { if (addItem(offer.id)) setAddedId(offer.id); };
  return <article id={groupId} className="nursery-kit-detail" aria-labelledby={`title-${groupId}`}>
    {offers.slice(1).map((item) => <span key={item.id} id={item.id} className="nursery-product-anchor" aria-hidden="true" />)}
    <figure className="nursery-kit-detail__media">
      <img src={kitImages[offer.id]} alt={copy.kitAlt[offer.id]} width="1000" height="750" loading="lazy" decoding="async" />
      <figcaption>{copy.illustration}</figcaption>
    </figure>
    <div>
      <span className="nursery-status">{copy.assembly}</span>
      <h2 id={`title-${groupId}`}>{product.title}</h2>
      <p>{product.description}</p>
      {offers.length > 1 && <fieldset className="nursery-variants" disabled={cartLocked}>
        <legend>{copy.configuration}</legend>
        {offers.map((item) => <label key={item.id} className={offer.id === item.id ? 'nursery-variant nursery-variant--selected' : 'nursery-variant'}>
          <input type="radio" name={`configuration-${groupId}`} value={item.id} checked={offer.id === item.id} onChange={() => setSelection({ hash: variantHash, id: item.id })} />
          <span>{item.id === 'pump' ? copy.pumpOnly : copy.pumpWithDrip}<strong>{formatShopMoney(item.priceMinor, locale, currency)}</strong></span>
        </label>)}
      </fieldset>}
      <h3>{copy.contents}</h3>
      <ul>{product.contents.map((item) => <li key={item}>{item}</li>)}</ul>
      {offer.id === 'soil-sensor' && <p className="nursery-small">{locale === 'en' ? 'A compatible Zigbee hub is needed and is sold separately.' : 'Нужен совместимый Zigbee-хаб; он приобретается отдельно.'}</p>}
      <div className="nursery-purchase">
        <div><strong className="nursery-price">{formatShopMoney(offer.priceMinor, locale, currency)}</strong><p>{copy.priceNote}</p></div>
        <button type="button" className="nursery-button" onClick={addToCart} disabled={cartLocked} aria-label={`${copy.add}: ${product.title}`}>{copy.add}<ArrowRight aria-hidden="true" size={18} /></button>
      </div>
      <div className="nursery-added" role="status" aria-live="polite">{addedId === offer.id && <><Check aria-hidden="true" size={18} /><span>{copy.added}</span><Link className="nursery-text-link" to={locale === 'en' ? '/en/cart/' : '/korzina/'}>{copy.cart}</Link></>}</div>
    </div>
  </article>;
}

export function NurseryHelpCard({ content, className = '' }) {
  const { openConsultation } = useShop();
  return <aside className={`nursery-help-card ${className}`}>
    <div className="nursery-help-card__copy"><h3>{content.title}</h3><p>{content.text}</p></div>
    <img src={content.image} alt={content.image_alt} width="640" height="800" loading="lazy" decoding="async" />
    <div className="nursery-help-card__footer"><button type="button" className="nursery-button nursery-button--outline" onClick={openConsultation}>{content.cta}</button></div>
  </aside>;
}

export function NurseryDemoBanner({ content, placement }) {
  if (!DEMO_PUBLIC_ENABLED) return null;
  return <section className="nursery-demo-banner">
    <div className="nursery-demo-banner__decoration" aria-hidden="true"><Sprout size={74} strokeWidth={1.1} /><Heart className="nursery-demo-banner__heart" size={26} strokeWidth={1.2} /><span>GrowerHub</span></div>
    <div><h2>{content.title}</h2><p>{content.text}</p><DemoStartLink className="nursery-text-link" placement={placement}>{content.cta}<ArrowRight aria-hidden="true" size={19} /></DemoStartLink><p className="nursery-small">{content.note}</p></div>
  </section>;
}

export function NurseryFootnote({ children, connection = false }) {
  const Icon = connection ? Wifi : Info;
  return <p className="nursery-footnote"><Icon aria-hidden="true" size={21} /><span>{children}</span></p>;
}
