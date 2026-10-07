import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { createShopRequest } from '../../api/shop';
import { getPublicPath } from '../../domain/localizedRoutes';
import { trackProductGoal } from '../../utils/analytics';
import { useShop } from './ShopContext';
import { buildRequest, newDraft, validateDraft } from './model';
import { formatShopMoney, shopCopy } from './copy';

export function RequestReceipt({ receipt, kind }) {
  const { locale } = useShop();
  const t = shopCopy[locale];
  return <section className="gh-shop-receipt ym-hide-content" role="status">
    <span className="gh-shop-receipt__heart" aria-hidden="true">♡</span>
    <h2>{kind === 'ORDER' ? t.orderReceived : t.consultationReceived}</h2>
    <p>{kind === 'ORDER' ? t.success : t.consultationSuccess}</p>
    <p><strong>{t.number}: {receipt.number}</strong></p>
    {kind === 'ORDER' ? <p>{t.total}: {formatShopMoney(receipt.totalMinor, locale, receipt.currency)}</p> : null}
    <p className="gh-shop-muted">{t.saveNumber}</p>
  </section>;
}

export default function RequestForm({ kind, onSaved }) {
  const { catalog, catalogState, refreshCatalog, locale, items, setCartLocked, requestDrafts } = useShop();
  const t = shopCopy[locale];
  const prefix = useId();
  const formRef = useRef(null);
  const savedDraft = requestDrafts.current[kind];
  const [draft, setDraft] = useState(() => savedDraft?.draft || newDraft());
  const [errorKey, setErrorKey] = useState(() => savedDraft?.attempt ? 'uncertain' : '');
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(Boolean(savedDraft?.attempt));
  const [conflict, setConflict] = useState(false);
  const attempt = useRef(savedDraft?.attempt || null);
  const submitting = useRef(false);
  const started = useRef(false);
  const unknownItems = kind === 'ORDER' && items.some((item) => !catalog.offers.some((offer) => offer.id === item.offerId));
  const available = catalogState === 'ready' && catalog.acceptingRequests === true;
  const locked = busy || uncertain || conflict;

  useEffect(() => {
    requestDrafts.current[kind] = { draft, attempt: attempt.current };
  }, [draft, kind, requestDrafts, busy, uncertain]);

  const change = (field, value) => {
    if (locked) return;
    setDraft((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    if (errorKey !== 'catalogChanged') setErrorKey('');
  };
  const trackStart = () => {
    if (!started.current && kind === 'ORDER') {
      trackProductGoal('shop_checkout_start', { placement: 'cart' });
      started.current = true;
    }
  };
  const submit = async (event) => {
    event.preventDefault();
    if (submitting.current || conflict || (!uncertain && (!available || unknownItems || (kind === 'ORDER' && !items.length)))) return;
    const nextErrors = uncertain ? {} : validateDraft(draft, kind, t);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) {
      setErrorKey('invalidFields');
      const firstName = Object.keys(nextErrors)[0];
      formRef.current?.elements.namedItem(firstName)?.focus();
      return;
    }
    if (!attempt.current) attempt.current = buildRequest(kind, draft, catalog, items, crypto.randomUUID());
    submitting.current = true;
    setBusy(true);
    setErrorKey('');
    if (kind === 'ORDER') setCartLocked(true);
    requestDrafts.current[kind] = { draft, attempt: attempt.current };
    try {
      const receipt = await createShopRequest(attempt.current);
      if (!receipt?.number || !Number.isSafeInteger(receipt.totalMinor) || receipt.totalMinor < 0 || receipt.currency !== 'RUB' || !receipt.createdAt || receipt.status !== 'NEW') throw new Error('Unconfirmed shop request');
      attempt.current = null;
      requestDrafts.current[kind] = { draft: newDraft(), attempt: null, receipt };
      setUncertain(false);
      if (kind === 'ORDER') setCartLocked(false);
      trackProductGoal('shop_request_created', { placement: kind === 'ORDER' ? 'cart' : 'consultation', action: kind.toLowerCase() });
      onSaved(receipt);
    } catch (error) {
      if (error.code === 'IDEMPOTENCY_CONFLICT') {
        setConflict(true);
        setErrorKey('conflict');
      } else if (error.status && error.status < 500) {
        attempt.current = null;
        setUncertain(false);
        if (kind === 'ORDER') setCartLocked(false);
        if (error.code === 'CATALOG_CHANGED') {
          await refreshCatalog();
          setErrorKey('catalogChanged');
        } else setErrorKey(error.status === 429 ? 'rateLimited' : 'requestInvalid');
      } else if (error.code === 'SHOP_UNAVAILABLE') {
        attempt.current = null;
        setUncertain(false);
        if (kind === 'ORDER') setCartLocked(false);
        setErrorKey('unavailable');
        await refreshCatalog();
      } else {
        setUncertain(true);
        setErrorKey('uncertain');
      }
    } finally {
      requestDrafts.current[kind] = { ...(requestDrafts.current[kind] || {}), attempt: attempt.current };
      submitting.current = false;
      setBusy(false);
    }
  };

  const field = (name, label, { maxLength, type = 'text', autoComplete = 'off', hint, required = false, multiline = false } = {}) => {
    const id = `${prefix}-${name}`;
    const props = {
      id, name, value: draft[name], maxLength, autoComplete, required, readOnly: locked,
      className: 'ym-disable-keys', 'aria-invalid': Boolean(errors[name]),
      'aria-describedby': [hint ? `${id}-hint` : '', errors[name] ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined,
      onChange: (event) => change(name, event.target.value),
    };
    return <div className="gh-shop-field" key={name}>
      <label htmlFor={id}>{label}</label>
      {multiline ? <textarea {...props} rows={3} /> : <input {...props} type={type} />}
      {hint ? <span id={`${id}-hint`} className="gh-shop-hint">{hint}</span> : null}
      {errors[name] ? <span id={`${id}-error`} className="gh-shop-field-error">{errors[name]}</span> : null}
    </div>;
  };

  return <form ref={formRef} className="gh-shop-form ym-hide-content" noValidate onSubmit={submit} onFocus={trackStart} aria-busy={busy}>
    {catalogState === 'loading' ? <p role="status" className="gh-shop-hint">{t.catalogLoading}</p> : null}
    {catalogState === 'error' ? <div className="gh-shop-notice"><p>{t.catalogError}</p><button type="button" className="gh-shop-button gh-shop-button--secondary" onClick={refreshCatalog}>{t.catalogRetry}</button></div> : null}
    {catalogState === 'ready' && !catalog.acceptingRequests ? <p className="gh-shop-notice">{t.shopClosed}</p> : null}
    {kind === 'CONSULTATION' ? <p>{t.consultationIntro}</p> : null}
    <fieldset>
      <legend>{t.contactsTitle}</legend>
      {field('name', t.name, { maxLength: 100, autoComplete: 'name', required: true })}
      {field('phone', t.phone, { maxLength: 32, type: 'tel', autoComplete: 'tel', required: true })}
      {field('telegram', t.telegram, { maxLength: 100, hint: t.telegramHint })}
    </fieldset>
    {kind === 'ORDER' ? <fieldset>
      <legend>{t.pickupTitle}</legend>
      <p className="gh-shop-hint">{t.pickupIntro}</p>
      <a className="gh-shop-external" href="https://www.cdek.ru/ru/offices/" target="_blank" rel="noopener noreferrer">{t.findPickup}<span aria-hidden="true"> ↗</span></a>
      {field('city', t.city, { maxLength: 120, autoComplete: 'address-level2', required: true })}
      {field('code', t.pickupCode, { maxLength: 40, hint: t.pickupCodeHint, required: true })}
      {field('address', t.pickupAddress, { maxLength: 300, required: true })}
    </fieldset> : null}
    {field('comment', kind === 'ORDER' ? t.comment : t.consultationComment, { maxLength: 2000, multiline: true, hint: kind === 'ORDER' ? t.commentHint : undefined })}
    <div className="gh-shop-honeypot" aria-hidden="true">
      <label htmlFor={`${prefix}-website`}>Website</label>
      <input id={`${prefix}-website`} name="website" type="text" tabIndex={-1} autoComplete="off" value={draft.website} onChange={(event) => change('website', event.target.value)} />
    </div>
    <div className="gh-shop-consent">
      <input id={`${prefix}-consent`} name="consent" type="checkbox" required checked={draft.consent} disabled={locked} onChange={(event) => change('consent', event.target.checked)} aria-invalid={Boolean(errors.consent)} aria-describedby={errors.consent ? `${prefix}-consent-error` : undefined} />
      <div><label htmlFor={`${prefix}-consent`}>{t.consent}</label><p><Link to={getPublicPath('privacy', locale)} target="_blank" rel="noopener noreferrer">{t.privacy}</Link>{' · '}<Link to={getPublicPath('terms', locale)} target="_blank" rel="noopener noreferrer">{t.terms}</Link></p>
        {errors.consent ? <span id={`${prefix}-consent-error`} className="gh-shop-field-error">{errors.consent}</span> : null}</div>
    </div>
    {errorKey ? <p className="gh-shop-error" role="alert">{t[errorKey]}</p> : null}
    <button type="submit" className="gh-shop-button" disabled={busy || conflict || (!uncertain && (!available || unknownItems || (kind === 'ORDER' && !items.length)))}>
      {busy ? t.submitting : uncertain ? (kind === 'ORDER' ? t.retrySubmit : t.consultationRetry) : (kind === 'ORDER' ? t.submit : t.consultationSubmit)}
    </button>
  </form>;
}
