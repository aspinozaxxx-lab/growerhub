import { useState } from 'react';
import { Link } from 'react-router-dom';
import { kitImages } from '../../components/nursery/copy';
import { getPublicPath } from '../../domain/localizedRoutes';
import { useShop } from './ShopContext';
import { formatShopMoney, getOfferCopy, shopCopy } from './copy';
import { MAX_QUANTITY } from './model';
import RequestForm, { RequestReceipt } from './RequestForm';

export default function ShopCart() {
  const { items, catalog, locale, updateQuantity, removeItem, clearCart, cartLocked, openConsultation, requestDrafts } = useShop();
  const t = shopCopy[locale];
  const [receipt, setReceipt] = useState(() => items.length ? null : requestDrafts.current.ORDER?.receipt);
  const lines = items.map((item) => ({ ...item, offer: catalog.offers.find((offer) => offer.id === item.offerId) }));
  const total = lines.reduce((sum, line) => sum + (line.offer?.priceMinor || 0) * line.quantity, 0);
  const saved = (next) => { setReceipt(next); clearCart(); };

  return <div className="gh-shop">
    <header className="gh-shop-heading">
      <span className="gh-shop-eyebrow" aria-hidden="true">♡ GrowerHub</span>
      <h1>{t.cartTitle}</h1>
      <p>{t.cartIntro}</p>
    </header>
    {receipt ? <>
      <RequestReceipt receipt={receipt} kind="ORDER" />
      <Link className="gh-shop-button gh-shop-button--secondary" to={getPublicPath('equipment', locale)}>{t.continueShopping}</Link>
    </> : !items.length ? <section className="gh-shop-empty">
      <span aria-hidden="true">♡</span><h2>{t.emptyTitle}</h2><p>{t.emptyText}</p>
      <div className="gh-shop-actions"><Link className="gh-shop-button" to={getPublicPath('equipment', locale)}>{t.chooseKits}</Link><button type="button" className="gh-shop-button gh-shop-button--secondary" onClick={openConsultation}>{t.needHelp}</button></div>
    </section> : <div className="gh-shop-checkout">
      <section className="gh-shop-summary" aria-label={locale === 'en' ? 'Selected products' : 'Выбранные товары'}>
        {lines.map(({ offerId, quantity, offer }) => {
          const copy = getOfferCopy(offerId, locale);
          return <article className="gh-shop-line" key={offerId}>
            {kitImages[offerId] ? <img src={kitImages[offerId]} alt={copy.title} width="240" height="180" /> : null}
            <div className="gh-shop-line__body"><h2>{copy.title}</h2><p>{copy.contents.slice(0, 2).join(' · ')}</p>
              {offer ? <p className="gh-shop-line__price">{formatShopMoney(offer.priceMinor, locale, catalog.currency)}</p> : <p className="gh-shop-error">{t.unknownItem}</p>}
              <div className="gh-shop-line__controls"><label htmlFor={`quantity-${offerId}`}>{t.quantity}<select id={`quantity-${offerId}`} value={quantity} disabled={cartLocked} onChange={(event) => updateQuantity(offerId, event.target.value)}>{Array.from({ length: MAX_QUANTITY }, (_, index) => <option key={index + 1} value={index + 1}>{index + 1}</option>)}</select></label>
                <button type="button" className="gh-shop-remove" disabled={cartLocked} onClick={() => removeItem(offerId)} aria-label={`${t.remove}: ${copy.title}`}>{t.remove}</button></div>
            </div>
          </article>;
        })}
        <div className="gh-shop-total"><span>{t.total}</span><strong>{formatShopMoney(total, locale, catalog.currency)}</strong></div>
        <p className="gh-shop-hint">{t.deliveryNote}</p><p className="gh-shop-hint">{t.ownLamps}</p>
        {cartLocked ? <p role="status" className="gh-shop-notice">{t.cartLocked}</p> : null}
        <Link className="gh-shop-back" to={getPublicPath('equipment', locale)}>{t.continueShopping}</Link>
      </section>
      <section className="gh-shop-checkout__form"><RequestForm kind="ORDER" onSaved={saved} /></section>
    </div>}
  </div>;
}
