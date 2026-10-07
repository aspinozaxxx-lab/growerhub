import { useState } from 'react';
import { Link } from 'react-router-dom';
import { careRequest } from '../../api/care';
import { CARE_ACTIONS, careUtc } from './careModel';
import { formatDateLong, formatDateTimeInput, formatTimeHHMM, parseBackendTimestamp } from '../../utils/formatters';
import { translateApp as t } from '../../locales/i18n';
import Button from '../../components/ui/Button';
import Modal from '../../components/ui/Modal';
import FormField from '../../components/ui/FormField';

export default function CareReminders({ reminders, plantId, onChanged }) {
  const [editor, setEditor] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [form, setForm] = useState({ action: 'watering', title: t('Проверить грунт и при необходимости полить'), dueAt: formatDateTimeInput(new Date(Date.now() + 86400000)), repeatDays: '', repeatMode: 'completion' });
  const change = (key, value) => setForm(old => ({ ...old, [key]: value }));
  async function run(fn) { if (busy) return; setBusy(true); setError(''); try { await fn(); await onChanged(); } catch (e) { setError(e.message); } finally { setBusy(false); } }
  const action = (r, kind) => run(() => careRequest(`/reminders/${r.id}/${kind}`, 'POST', { occurrenceKey: r.occurrenceKey, dueAt: kind === 'snooze' ? new Date(Date.now() + 86400000).toISOString().slice(0, 19) : null }));
  return <section className="care-reminders ym-hide-content">
    <div className="care-section-heading"><h2>{t(plantId ? 'Следующий уход' : 'Дела и напоминания')}</h2>{plantId && <Button onClick={() => setEditor(true)}>{t('Напомнить')}</Button>}</div>
    {error && <p role="alert">{error}</p>}
    {!reminders.length && <p className="care-muted">{t('Здесь будут дела, которые вы назначите растениям.')}</p>}
    {reminders.map(r => <article key={r.id} className="care-reminder">
      {!plantId && <Link to={`/app/plants/${r.plantId}/journal/`}>{r.plantName}</Link>}
      <strong>{r.title}</strong><span className={parseBackendTimestamp(r.dueAt) < new Date() ? 'care-due' : 'care-muted'}>{formatDateLong(r.dueAt)} · {formatTimeHHMM(r.dueAt)}</span>
      {r.action === 'watering' && <small>{t('Сначала проверьте, нужен ли растению полив.')}</small>}
      <div className="care-actions"><Button disabled={busy} onClick={() => action(r, 'done')}>{t(r.action === 'watering' ? 'Полито вручную' : 'Выполнено')}</Button><Button disabled={busy} onClick={() => action(r, 'snooze')}>{t('Завтра')}</Button><Button disabled={busy} onClick={() => action(r, 'skip')}>{t('Пропустить')}</Button><button type="button" disabled={busy} onClick={() => { setForm({ action: r.action, title: r.title, dueAt: formatDateTimeInput(r.dueAt), repeatDays: r.repeatDays || '', repeatMode: r.repeatMode }); setEditor(r); }}>{t('Изменить')}</button><button type="button" disabled={busy} onClick={() => { if (window.confirm(t('Удалить это напоминание?'))) run(() => careRequest(`/reminders/${r.id}`, 'DELETE')); }}>{t('Удалить')}</button></div>
    </article>)}
    {editor && <Modal isOpen title={t('Напоминание об уходе')} onClose={busy ? undefined : () => setEditor(false)} footer={<Button disabled={busy} variant="primary" form="care-reminder-form" type="submit">{t('Сохранить')}</Button>}>
      <form className="care-form" id="care-reminder-form" onSubmit={e => { e.preventDefault(); run(async () => { await careRequest(editor?.id ? `/reminders/${editor.id}` : '/reminders', editor?.id ? 'PATCH' : 'POST', { ...form, plantId: Number(plantId), dueAt: careUtc(form.dueAt), repeatDays: form.repeatDays ? Number(form.repeatDays) : null }); setEditor(false); }); }}>
        {error && <p role="alert">{error}</p>}
        <FormField label={t('Название')} htmlFor="rem-title"><input id="rem-title" required maxLength={200} value={form.title} onChange={e => change('title', e.target.value)} /></FormField>
        <FormField label={t('Вид ухода')} htmlFor="rem-action"><select id="rem-action" value={form.action} onChange={e => change('action', e.target.value)}>{CARE_ACTIONS.map(([key, label]) => <option key={key} value={key}>{t(label)}</option>)}</select></FormField>
        <FormField label={t('Дата и время')} htmlFor="rem-date"><input id="rem-date" type="datetime-local" required value={form.dueAt} onChange={e => change('dueAt', e.target.value)} /></FormField>
        <FormField label={t('Повторять через дней (необязательно)')} htmlFor="rem-repeat"><input id="rem-repeat" type="number" min="1" max="3650" value={form.repeatDays} onChange={e => change('repeatDays', e.target.value)} /></FormField>
        {form.repeatDays && <FormField label={t('Отсчитывать повтор')} htmlFor="rem-mode"><select id="rem-mode" value={form.repeatMode} onChange={e => change('repeatMode', e.target.value)}><option value="completion">{t('После выполнения')}</option><option value="calendar">{t('По календарю')}</option></select></FormField>}
        <small>{t('Напоминание предлагает проверить растение. Это не команда автоматического полива.')}</small>
      </form>
    </Modal>}
  </section>;
}
