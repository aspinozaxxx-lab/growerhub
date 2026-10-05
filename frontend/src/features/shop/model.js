export const CART_STORAGE_KEY = 'growerhub_shop_cart_v1';
export const MAX_QUANTITY = 10;

export function sanitizeCart(value) {
  if (!Array.isArray(value)) return [];
  const combined = new Map();
  value.slice(0, 30).forEach((item) => {
    if (!item || typeof item.offerId !== 'string' || !/^[a-z0-9-]{1,64}$/u.test(item.offerId)) return;
    const quantity = Number(item.quantity);
    if (!Number.isInteger(quantity) || quantity < 1) return;
    combined.set(item.offerId, Math.min(MAX_QUANTITY, quantity + (combined.get(item.offerId) || 0)));
  });
  return [...combined].slice(0, 3).map(([offerId, quantity]) => ({ offerId, quantity }));
}

export const newDraft = () => ({ name: '', phone: '', telegram: '', city: '', code: '', address: '', comment: '', consent: false, website: '' });

export function validateDraft(draft, kind, copy) {
  const errors = {};
  ['name', 'phone', ...(kind === 'ORDER' ? ['city', 'code', 'address'] : [])].forEach((field) => {
    if (!draft[field].trim()) errors[field] = copy.required;
  });
  const phone = draft.phone.trim();
  if (phone && (!/^[+\d\s()-]+$/u.test(phone) || !/^\d{10,15}$/u.test(phone.replace(/\D/gu, '')))) errors.phone = copy.invalidPhone;
  if (!draft.consent) errors.consent = copy.requiredConsent;
  return errors;
}

export function buildRequest(kind, draft, catalog, items, idempotencyKey) {
  return {
    kind, idempotencyKey, catalogVersion: catalog.version,
    items: kind === 'ORDER' ? items.map(({ offerId, quantity }) => ({ offerId, quantity })) : [],
    customer: { name: draft.name.trim(), phone: draft.phone.trim(), telegram: draft.telegram.trim() || null },
    pickup: kind === 'ORDER' ? { city: draft.city.trim(), code: draft.code.trim(), address: draft.address.trim() } : null,
    comment: draft.comment.trim(), consent: draft.consent, website: draft.website,
  };
}
