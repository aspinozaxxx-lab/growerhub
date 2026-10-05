import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import catalogSnapshot from '../../../../backend/src/main/resources/shop/catalog.json';
import { fetchShopCatalog } from '../../api/shop';
import i18n, { getCurrentLocale } from '../../locales/i18n';
import { isAppPath } from '../../domain/localizedRoutes';
import { trackProductGoal } from '../../utils/analytics';
import { ShopContext } from './ShopContext';
import { CART_STORAGE_KEY, MAX_QUANTITY, sanitizeCart } from './model';
import ConsultationDialog from './ConsultationDialog';
import './shop.css';

function validCatalog(value) {
  return value && typeof value.version === 'string' && value.currency === 'RUB'
    && Array.isArray(value.offers) && value.offers.length > 0
    && value.offers.every((offer) => typeof offer.id === 'string' && Number.isSafeInteger(offer.priceMinor) && offer.priceMinor >= 0);
}

export default function ShopProvider({ children, initialCatalog = catalogSnapshot }) {
  useTranslation('common', { i18n });
  const locale = getCurrentLocale();
  const inApp = isAppPath(useLocation().pathname);
  const [catalog, setCatalog] = useState(initialCatalog);
  const [catalogState, setCatalogState] = useState('loading');
  const [items, setItems] = useState([]);
  const [storageReady, setStorageReady] = useState(false);
  const [consultationOpen, setConsultationOpen] = useState(false);
  const [cartLocked, setCartLocked] = useState(false);
  const requestDrafts = useRef({});
  const requestGeneration = useRef(0);
  const mounted = useRef(false);

  const refreshCatalog = useCallback(async () => {
    const generation = ++requestGeneration.current;
    setCatalogState('loading');
    try {
      const next = await fetchShopCatalog();
      if (!validCatalog(next)) throw new Error('Invalid shop catalogue');
      if (mounted.current && generation === requestGeneration.current) {
        setCatalog(next);
        setCatalogState('ready');
      }
      return next;
    } catch {
      if (mounted.current && generation === requestGeneration.current) setCatalogState('error');
      return null;
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    try { setItems(sanitizeCart(JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY) || '[]'))); }
    catch { setItems([]); }
    setStorageReady(true);
    return () => { mounted.current = false; requestGeneration.current += 1; };
  }, []);

  useEffect(() => {
    if (!inApp) refreshCatalog();
  }, [inApp, refreshCatalog]);

  useEffect(() => {
    if (!storageReady) return;
    try { window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(sanitizeCart(items))); }
    catch { /* Translitem: nedostupnyj storage ne dolzhen meshat zakazu. */ }
  }, [items, storageReady]);

  const addItem = useCallback((offerId, quantity = 1) => {
    if (cartLocked || !catalog.offers.some((offer) => offer.id === offerId)) return false;
    const amount = Number(quantity);
    if (!Number.isInteger(amount) || amount < 1 || amount > MAX_QUANTITY) return false;
    setItems((current) => sanitizeCart([...current, { offerId, quantity: amount }]));
    trackProductGoal('shop_offer_select', { placement: 'equipment', action: offerId });
    return true;
  }, [cartLocked, catalog]);
  const updateQuantity = useCallback((offerId, quantity) => {
    if (cartLocked) return;
    const amount = Number(quantity);
    if (!Number.isInteger(amount) || amount < 1 || amount > MAX_QUANTITY) return;
    setItems((current) => current.map((item) => item.offerId === offerId ? { offerId, quantity: amount } : item));
  }, [cartLocked]);
  const removeItem = useCallback((offerId) => {
    if (!cartLocked) setItems((current) => current.filter((item) => item.offerId !== offerId));
  }, [cartLocked]);
  const clearCart = useCallback(() => { setItems([]); setCartLocked(false); }, []);
  const openConsultation = useCallback(() => setConsultationOpen(true), []);
  const value = useMemo(() => ({
    catalog, catalogState, refreshCatalog, locale, items, cartCount: items.reduce((sum, item) => sum + item.quantity, 0),
    addItem, updateQuantity, removeItem, clearCart, cartLocked, setCartLocked, openConsultation, requestDrafts,
  }), [catalog, catalogState, refreshCatalog, locale, items, addItem, updateQuantity, removeItem, clearCart, cartLocked, openConsultation]);

  return <ShopContext.Provider value={value}>
    {children}
    {!inApp ? <ConsultationDialog isOpen={consultationOpen} onClose={() => setConsultationOpen(false)} /> : null}
  </ShopContext.Provider>;
}
