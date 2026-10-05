import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { fetchAdminShopRequest, fetchAdminShopRequests, retryShopNotification, updateAdminShopRequest } from '../../api/shop';
import i18n, { getCurrentLocale } from '../../locales/i18n';
import { formatDateTimeDDMMYYYY } from '../../utils/formatters';
import { formatShopMoney, getOfferCopy } from './copy';

const adminCopy = {
  ru: {
    title: 'Заказы и консультации', refresh: 'Обновить', all: 'Все статусы', filter: 'Статус заявок',
    loading: 'Загружаем заявки…', empty: 'Заявок пока нет', error: 'Не удалось загрузить заявки. Попробуйте ещё раз.',
    saveError: 'Не удалось сохранить изменение. Предыдущее состояние сохранено.',
    retryError: 'Не удалось повторить уведомление. Обновите список и проверьте его состояние.',
    detailError: 'Заявка по ссылке не найдена или недоступна.', found: 'Всего заявок', previous: 'Назад', next: 'Далее', page: 'Страница',
    order: 'Заказ комплекта', consultation: 'Консультация', total: 'Сумма комплектов', customer: 'Контакт', pickup: 'Пункт СДЭК',
    comment: 'Комментарий', notification: 'Уведомление Telegram', attempts: 'Попыток', notificationError: 'Код ошибки',
    uncertain: 'Результат отправки неизвестен. Не повторяем автоматически, чтобы не дублировать сообщение.',
    checkedChat: 'Я проверил чат: этого уведомления там нет',
    accepted: 'Telegram принял сообщение. Это не подтверждение прочтения.', retry: 'Повторить уведомление', status: 'Статус заявки', save: 'Сохранить статус', saving: 'Сохраняем…',
    noPayment: 'Заявка без оплаты. Перед подтверждением проверьте совместимость, комплектацию и стоимость доставки с покупателем.',
    direct: 'Заявка по ссылке', verification: 'Проверка модели обязательна перед подтверждением поставки.',
    statuses: { NEW: 'Новая', PROCESSING: 'В работе', CONFIRMED: 'Подтверждена', CLOSED: 'Закрыта' },
    notifications: { queued: 'В очереди', sending: 'Отправляется', accepted: 'Принято Telegram', failed: 'Ошибка', uncertain: 'Результат неизвестен', blocked: 'Доставка заблокирована' },
  },
  en: {
    title: 'Orders and consultations', refresh: 'Refresh', all: 'All statuses', filter: 'Request status',
    loading: 'Loading requests…', empty: 'No requests yet', error: 'Could not load requests. Please try again.',
    saveError: 'Could not save the change. The previous status is unchanged.',
    retryError: 'Could not retry the notification. Refresh the list and check its status.',
    detailError: 'The linked request was not found or is unavailable.', found: 'Total requests', previous: 'Previous', next: 'Next', page: 'Page',
    order: 'Kit order', consultation: 'Consultation', total: 'Kit total', customer: 'Contact', pickup: 'CDEK pickup point',
    comment: 'Comment', notification: 'Telegram notification', attempts: 'Attempts', notificationError: 'Error code',
    uncertain: 'The send result is unknown. We do not retry automatically to avoid a duplicate message.',
    checkedChat: 'I checked the chat: this notification is not there',
    accepted: 'Telegram accepted the message. This does not confirm it was read.', retry: 'Retry notification', status: 'Request status', save: 'Save status', saving: 'Saving…',
    noPayment: 'No payment has been taken. Confirm compatibility, contents and delivery cost with the customer before confirming the order.',
    direct: 'Linked request', verification: 'The model must be tested before supply is confirmed.',
    statuses: { NEW: 'New', PROCESSING: 'In progress', CONFIRMED: 'Confirmed', CLOSED: 'Closed' },
    notifications: { queued: 'Queued', sending: 'Sending', accepted: 'Accepted by Telegram', failed: 'Failed', uncertain: 'Unknown result', blocked: 'Delivery blocked' },
  },
};

function RequestCard({ request, locale, busy, onStatus, onRetry, initiallyOpen = false }) {
  const t = adminCopy[locale];
  const [statusDraft, setStatusDraft] = useState({ base: request.status, value: request.status });
  const [retryConfirmation, setRetryConfirmation] = useState(null);
  const status = statusDraft.base === request.status ? statusDraft.value : request.status;
  const notification = request.notification || {};
  const retryKey = `${request.id}:${notification.status}:${notification.attempts}:${notification.updatedAt}`;
  return <details className="gh-shop-admin__request" open={initiallyOpen || undefined}>
    <summary><span><strong>{request.number}</strong> · {request.kind === 'ORDER' ? t.order : t.consultation}<small>{formatDateTimeDDMMYYYY(request.createdAt)} · {request.customer.name}</small></span><span className="gh-shop-admin__badge">{t.statuses[request.status] || request.status}</span></summary>
    <div className="gh-shop-admin__details">
      <div className="gh-shop-admin__columns">
        <section><h3>{t.customer}</h3><p>{request.customer.name}</p><p><a href={`tel:${request.customer.phone.replace(/[^+\d]/gu, '')}`}>{request.customer.phone}</a></p>{request.customer.telegram ? <p>Telegram: {request.customer.telegram}</p> : null}</section>
        {request.pickup ? <section><h3>{t.pickup}</h3><p>{request.pickup.city} · {request.pickup.code}</p><p>{request.pickup.address}</p></section> : null}
      </div>
      {request.items?.length ? <section><ul>{request.items.map((item) => <li key={item.offerId}>{getOfferCopy(item.offerId, locale).title} × {item.quantity} — {formatShopMoney(item.unitPriceMinor * item.quantity, locale, request.currency)}<small>{item.hubModel} · {item.socketCount} {locale === 'en' ? 'plug(s) per kit' : 'розеток в комплекте'}{item.verification === 'PILOT' ? ` · ${t.verification}` : ''}</small></li>)}</ul><p><strong>{t.total}: {formatShopMoney(request.totalMinor, locale, request.currency)}</strong></p><p className="gh-shop-admin__hint">{t.noPayment}</p></section> : null}
      {request.comment ? <section><h3>{t.comment}</h3><p className="gh-shop-admin__comment">{request.comment}</p></section> : null}
      <section className="gh-shop-admin__notification"><h3>{t.notification}</h3><p>{t.notifications[notification.status] || '—'} · {t.attempts}: {notification.attempts ?? 0}</p>
        {notification.lastError ? <p className="gh-shop-admin__hint">{t.notificationError}: {notification.lastError}</p> : null}
        {notification.status === 'uncertain' ? <p>{t.uncertain}</p> : null}
        {notification.status === 'accepted' ? <p className="gh-shop-admin__hint">{t.accepted}</p> : null}
        {notification.status === 'uncertain' ? <label className="gh-shop-admin__retry-confirm"><input type="checkbox" checked={retryConfirmation === retryKey} disabled={busy} onChange={(event) => setRetryConfirmation(event.target.checked ? retryKey : null)} />{t.checkedChat}</label> : null}
        {['failed', 'blocked', 'uncertain'].includes(notification.status) ? <button type="button" onClick={() => { setRetryConfirmation(null); onRetry(request.id); }} disabled={busy || (notification.status === 'uncertain' && retryConfirmation !== retryKey)}>{t.retry}</button> : null}
      </section>
      <form className="gh-shop-admin__status" onSubmit={(event) => { event.preventDefault(); onStatus(request.id, status); }}>
        <label htmlFor={`shop-status-${request.id}`}>{t.status}<select id={`shop-status-${request.id}`} value={status} disabled={busy} onChange={(event) => setStatusDraft({ base: request.status, value: event.target.value })}>{Object.entries(t.statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <button type="submit" disabled={busy || status === request.status}>{busy ? t.saving : t.save}</button>
      </form>
    </div>
  </details>;
}

export default function AdminRequestsPanel() {
  useTranslation('common', { i18n });
  const locale = getCurrentLocale();
  const t = adminCopy[locale];
  const [params] = useSearchParams();
  const selectedId = params.get('request');
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const [data, setData] = useState({ requests: [], totalElements: 0, totalPages: 0 });
  const [direct, setDirect] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(null);
  const [errorKey, setErrorKey] = useState('');
  const generation = useRef(0);

  const load = useCallback(async () => {
    const requestGeneration = ++generation.current;
    setLoading(true);
    setErrorKey('');
    try {
      const next = await fetchAdminShopRequests({ page, size: 20, status });
      if (requestGeneration !== generation.current) return;
      if (!Array.isArray(next?.requests)) throw new Error('Invalid shop requests');
      setData(next);
      setDirect(null);
      if (selectedId && !next.requests.some((entry) => String(entry.id) === selectedId)) {
        try {
          const entry = await fetchAdminShopRequest(selectedId);
          if (requestGeneration === generation.current) setDirect(entry);
        } catch {
          if (requestGeneration === generation.current) setErrorKey('detailError');
        }
      }
    } catch { if (requestGeneration === generation.current) setErrorKey('error'); }
    finally { if (requestGeneration === generation.current) setLoading(false); }
  }, [page, status, selectedId]);
  useEffect(() => { load(); return () => { generation.current += 1; }; }, [load]);

  const update = async (id, action, payload) => {
    if (busy !== null) return;
    setBusy(id);
    setErrorKey('');
    try {
      const next = action === 'status' ? await updateAdminShopRequest(id, payload) : await retryShopNotification(id);
      if (!next?.id) throw new Error('Invalid shop request');
      setData((current) => ({ ...current, requests: current.requests.map((entry) => entry.id === id ? next : entry) }));
      setDirect((current) => current?.id === id ? next : current);
    } catch { setErrorKey(action === 'status' ? 'saveError' : 'retryError'); }
    finally { setBusy(null); }
  };
  const card = (entry, selected = false) => <RequestCard key={entry.id} request={entry} locale={locale} busy={busy !== null} onStatus={(id, nextStatus) => update(id, 'status', nextStatus)} onRetry={(id) => update(id, 'retry')} initiallyOpen={selected} />;

  return <div className="admin-page gh-shop-admin ym-hide-content">
    <header className="gh-shop-admin__header"><h1>{t.title}</h1><button type="button" onClick={load} disabled={loading || busy !== null}>{t.refresh}</button></header>
    <div className="gh-shop-admin__toolbar"><label htmlFor="shop-request-filter">{t.filter}<select id="shop-request-filter" value={status} disabled={busy !== null} onChange={(event) => { setStatus(event.target.value); setPage(0); }}><option value="">{t.all}</option>{Object.entries(t.statuses).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label><p>{t.found}: {data.totalElements}</p></div>
    {errorKey ? <p role="alert" className="gh-shop-admin__error">{t[errorKey]}</p> : null}
    {loading ? <p role="status">{t.loading}</p> : null}
    {direct ? <section><h2>{t.direct}</h2>{card(direct, true)}</section> : null}
    {!loading && !errorKey && !data.requests.length ? <p>{t.empty}</p> : null}
    {data.requests.map((entry) => card(entry, String(entry.id) === selectedId))}
    {data.totalPages > 1 ? <nav className="gh-shop-admin__pagination" aria-label={t.page}><button type="button" disabled={page === 0 || loading || busy !== null} onClick={() => setPage((current) => current - 1)}>{t.previous}</button><span>{t.page} {page + 1} / {data.totalPages}</span><button type="button" disabled={page >= data.totalPages - 1 || loading || busy !== null} onClick={() => setPage((current) => current + 1)}>{t.next}</button></nav> : null}
  </div>;
}
