import { useEffect, useState } from 'react';
import { assistantRequest } from '../../api/assistant';
import { translateApp as t } from '../../locales/i18n';
import Button from '../../components/ui/Button';
import './assistant.css';

const states = { new: 'На рассмотрении', planned: 'Запланировано', declined: 'Пока не планируем', done: 'Готово' };
export default function FeatureRequests() {
  const [items, setItems] = useState([]); const [page, setPage] = useState(0);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const load = async () => {
    setBusy(true); setError('');
    try { setItems(await assistantRequest(`/requests/admin?page=${page}`)); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  useEffect(() => { load(); }, [page]);
  const save = async (event, item) => {
    event.preventDefault(); const form = new FormData(event.currentTarget); setBusy(true); setError('');
    try {
      const updated = await assistantRequest(`/requests/admin/${item.id}`, 'PUT', { status: form.get('status'), note: form.get('note'), revision: item.revision });
      setItems(current => current.map(r => r.id === updated.id ? updated : r));
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  return <section className="assistant-requests ym-hide-content ym-disable-keys"><h2>{t('Запросы на доработки')}</h2>
    <p>{t('Пользователи подтвердили отправку этих задач. Решение и комментарий доступны автору через помощника.')}</p>
    {error && <p role="alert">{error}</p>}
    {!items.length && !busy && <p>{t('Заявок на этой странице пока нет.')}</p>}
    {items.map(item => <form className="assistant-proposal" key={`${item.id}:${item.revision}`} onSubmit={e => save(e, item)}>
      <h3>{item.title}</h3><small>{t('Пользователь №{{id}}', { id: item.userId })} · {new Date(`${item.createdAt}Z`).toLocaleString()} · {t(item.notificationQueued ? 'Уведомление поставлено в очередь Telegram' : 'Уведомление ещё не поставлено в очередь Telegram')}</small>
      <p>{item.description}</p><p><strong>{t('Зачем это нужно:')}</strong> {item.benefit}</p>
      <label>{t('Решение')}<select name="status" defaultValue={item.status}>{Object.entries(states).map(([value, label]) => <option key={value} value={value}>{t(label)}</option>)}</select></label>
      <label>{t('Комментарий для пользователя')}<textarea name="note" rows="2" maxLength={1000} defaultValue={item.note} /></label>
      <Button disabled={busy} type="submit">{t('Сохранить решение')}</Button>
    </form>)}
    <div className="assistant-actions"><Button disabled={busy || page === 0} onClick={() => setPage(page - 1)}>{t('Назад')}</Button><span>{page + 1}</span><Button disabled={busy || !items.length} onClick={() => setPage(page + 1)}>{t('Далее')}</Button><Button disabled={busy} onClick={load}>{t('Обновить')}</Button></div>
  </section>;
}
