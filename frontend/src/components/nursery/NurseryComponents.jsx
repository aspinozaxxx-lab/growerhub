import { ArrowRight, BadgeCheck, Heart, Info, Leaf, Sprout, Truck, Wifi } from 'lucide-react';
import { Link } from 'react-router-dom';
import DemoStartLink from '../DemoStartLink';
import { getPublicPath } from '../../domain/localizedRoutes';
import { DEMO_PUBLIC_ENABLED } from '../../domain/siteConfig';
import { formatShopMoney, getOfferCopy, useShop } from '../../features/shop';
import { getNurseryCopy, kitImages } from './copy';
import './nursery.css';

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
      {offer.verification === 'PILOT' && <p className="nursery-kit-card__pilot">{copy.pilot}</p>}
      <Link className="nursery-button" to={`${getPublicPath('equipment', locale)}#${offer.id}`} aria-label={`${copy.details}: ${product.title}`}>
        {copy.details}<ArrowRight aria-hidden="true" size={19} />
      </Link>
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
