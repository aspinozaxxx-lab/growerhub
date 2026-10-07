import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { telegramRequest } from '../../api/care';
import { useAuth } from '../../features/auth/AuthContext';
import { translateApp as t } from '../../locales/i18n';
import Button from '../../components/ui/Button';
import FormField from '../../components/ui/FormField';
import '../../features/care/care.css';

export default function AppNotifications() {
  const { demoActive } = useAuth(); const [state, setState] = useState(null); const [link, setLink] = useState(null);
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const refresh = async () => setState(await telegramRequest());
  useEffect(() => { if (!demoActive) refresh().catch(e => setError(e.message)); }, [demoActive]);
  async function run(fn) { if (busy) return; setBusy(true); setError(''); setNotice(''); try { await fn(); await refresh(); } catch (e) { setError(e.message); } finally { setBusy(false); } }
  const set = (key, value) => setState(old => ({ ...old, [key]: value }));
  if (demoActive) return <div className="care-empty"><h1>{t('Напоминания в Telegram')}</h1><p>{t('Подключение доступно в личном аккаунте. Демоферма не отправляет сообщения.')}</p><Link to="/app/plants/">{t('Мои растения')}</Link></div>;
  const statusText = { accepted: 'Передано в Telegram', queued: 'В очереди', sending: 'Отправляется', uncertain: 'Результат доставки неизвестен', failed: 'Не удалось доставить', blocked: 'Бот заблокирован', stopped: 'Отключено командой /stop', connected: 'Подключено', disconnected: 'Отключено', retry: 'Не удалось доставить' };
  return <div className="care-page"><h1>{t('Напоминания в Telegram')}</h1><p>{t('Одна сводка назначенных дел в выбранное время. Без рекламы и управления насосами.')}</p>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {state && <section className="care-panel"><div className="care-section-heading"><h2>Telegram</h2><span>{t(state.connected ? 'Подключено' : 'Не подключено')}</span></div>
      {state.connected && <p className="ym-hide-content">{state.name}</p>}
      {!state.available && <p>{t('Подключение Telegram временно недоступно')}</p>}
      {state.available && <div className="care-actions"><Button disabled={busy} variant="primary" onClick={() => run(async () => setLink(await telegramRequest('/link', 'POST')))}>{t(state.connected ? 'Сменить Telegram' : 'Подключить Telegram')}</Button>{state.connected && <Button disabled={busy} onClick={() => run(async () => { await telegramRequest('', 'DELETE'); setLink(null); })}>{t('Отключить')}</Button>}</div>}
      {link && <div className="care-link-steps"><p>{t('1. Откройте бота и нажмите «Начать». Ссылка действует 15 минут.')}</p><a className="gh-btn gh-btn--primary" href={link.url} target="_blank" rel="noreferrer">{t('Открыть бота')}</a><p>{t('2. Вернитесь сюда и проверьте профиль.')}</p><Button disabled={busy} onClick={() => run(async () => {})}>{t('Я нажал «Начать»')}</Button></div>}
      {state.pendingName && <div className="care-panel ym-hide-content"><strong>{state.pendingName}</strong><p>{t('Это ваш Telegram? Подтвердите, чтобы получать напоминания в этот чат.')}</p><Button disabled={busy} variant="primary" onClick={() => run(async () => { await telegramRequest('/confirm', 'POST', { confirmation: state.confirmation }); setLink(null); })}>{t('Да, это мой профиль')}</Button></div>}
      {state.connected && <form className="care-form" onSubmit={e => { e.preventDefault(); run(async () => { await telegramRequest('', 'PUT', { enabled: state.enabled, hour: state.hour, quietFrom: state.quietFrom, quietUntil: state.quietUntil }); setNotice(t('Настройки сохранены')); }); }}>
        <label className="care-checkbox"><input type="checkbox" checked={state.enabled} onChange={e => set('enabled', e.target.checked)} />{t('Получать напоминания об уходе')}</label>
        <FormField label={t('Час ежедневной сводки')} htmlFor="tg-hour"><select id="tg-hour" value={state.hour} onChange={e => set('hour', Number(e.target.value))}>{Array.from({ length: 24 }, (_, h) => <option key={h} value={h}>{String(h).padStart(2, '0')}:00</option>)}</select></FormField>
        <div className="care-filters">{[['quietFrom', 'Тихие часы с'], ['quietUntil', 'Тихие часы до']].map(([key, label]) => <FormField key={key} label={t(label)} htmlFor={key}><select id={key} value={state[key]} onChange={e => set(key, Number(e.target.value))}>{Array.from({ length: 24 }, (_, h) => <option key={h} value={h}>{String(h).padStart(2, '0')}:00</option>)}</select></FormField>)}</div>
        <small>{t('Часовой пояс аккаунта')}: {state.timezone}. {t('Одинаковые часы отключают тихий период. Если сводка попадает в тихие часы, она придёт после их окончания.')}</small>
        <Button type="submit" variant="primary" disabled={busy}>{t('Сохранить')}</Button>
      </form>}
      {state.connected && <div className="care-actions"><Button disabled={busy || !state.enabled} onClick={() => run(async () => { await telegramRequest('/test', 'POST'); setNotice(t('Пробное сообщение поставлено в очередь. Обычно доставка занимает до минуты.')); })}>{t('Пробное сообщение')}</Button><Button disabled={busy} onClick={() => run(async () => {})}>{t('Обновить статус')}</Button></div>}
      {state.lastStatus && <p role="status">{t('Последняя доставка')}: {t(statusText[state.lastStatus] || 'В очереди')}</p>}
    </section>}
    <small>{t('Бот пока отвечает по-русски. Фото и заметки добавляются на сайте; в Telegram можно отмечать и переносить дела.')}</small>
  </div>;
}
