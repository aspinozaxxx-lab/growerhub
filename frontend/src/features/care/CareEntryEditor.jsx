import { useRef, useState } from 'react';
import { careRequest, uploadCarePhoto } from '../../api/care';
import { CARE_ACTIONS, careUtc, prepareCarePhoto } from './careModel';
import { formatDateTimeInput } from '../../utils/formatters';
import { translateApp as t } from '../../locales/i18n';
import Modal from '../../components/ui/Modal';
import CarePhoto from './CarePhoto';
import Button from '../../components/ui/Button';
import FormField from '../../components/ui/FormField';

export default function CareEntryEditor({ plantId, item, action = 'note', onClose, onSaved }) {
  const [form, setForm] = useState({ action: item?.action || item?.entry.type || action, text: item?.entry.text || '', eventAt: formatDateTimeInput(item?.entry.eventAt || new Date()) });
  const [files, setFiles] = useState([]); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [existingPhotos, setExistingPhotos] = useState(item?.entry.photos || []);
  const key = useRef(crypto.randomUUID()); const saved = useRef(item?.entry.id || null); const uploaded = useRef(new Set());
  const change = (field, value) => setForm(prev => ({ ...prev, [field]: value }));
  async function submit(event) {
    event.preventDefault(); if (busy) return; setBusy(true); setError('');
    try {
      const photos = []; for (const file of files) photos.push(await prepareCarePhoto(file));
      const body = { ...form, action: CARE_ACTIONS.some(([key]) => key === form.action) ? form.action : null, eventAt: careUtc(form.eventAt), clientKey: key.current };
      if (saved.current) await careRequest(`/entries/${saved.current}`, 'PATCH', body);
      else saved.current = (await careRequest(`/plants/${plantId}/entries`, 'POST', body)).entry.id;
      for (let i = 0; i < photos.length; i++) if (!uploaded.current.has(files[i])) { await uploadCarePhoto(saved.current, photos[i]); uploaded.current.add(files[i]); }
      onSaved(); onClose();
    } catch (err) { setError((saved.current ? `${t('Запись сохранена. Не все изменения или фото загрузились; можно повторить.')} ` : '') + err.message); }
    finally { setBusy(false); }
  }
  return <Modal isOpen onClose={busy ? undefined : onClose} title={t(item ? 'Редактировать запись' : 'Новая запись ухода')} disableOverlayClose footer={
    <div className="care-actions"><Button disabled={busy} onClick={onClose}>{t('Отмена')}</Button><Button variant="primary" type="submit" form="care-entry-form" disabled={busy}>{t(busy ? 'Сохранение...' : 'Сохранить')}</Button></div>
  }><form id="care-entry-form" className="care-form ym-hide-content" onSubmit={submit}>
    {error && <p role="alert">{error}</p>}
    <FormField label={t('Вид ухода')} htmlFor="care-action"><select id="care-action" value={form.action} disabled={busy} onChange={e => change('action', e.target.value)}>{!CARE_ACTIONS.some(([key]) => key === form.action) && <option value={form.action}>{t(form.action === 'feeding' ? 'Уход' : 'Заметка')}</option>}{CARE_ACTIONS.map(([id, label, emoji]) => <option key={id} value={id}>{emoji} {t(label)}</option>)}</select></FormField>
    <FormField label={t('Дата и время')} htmlFor="care-date"><input id="care-date" type="datetime-local" required value={form.eventAt} disabled={busy} onChange={e => change('eventAt', e.target.value)} /></FormField>
    <FormField label={t('Заметка')} htmlFor="care-text"><textarea id="care-text" rows={4} maxLength={8000} value={form.text} disabled={busy} placeholder={t('Что изменилось, что сделали, что заметили?')} onChange={e => change('text', e.target.value)} /></FormField>
    <FormField label={t('Фотографии')} htmlFor="care-photos"><input id="care-photos" type="file" accept="image/*" multiple disabled={busy} onChange={e => { const next = [...e.target.files]; if (next.length > 6 - existingPhotos.length) { setError(t('В одной записи — до 6 фотографий')); e.target.value = ''; return; } setFiles(next); }} /></FormField>
    {!!existingPhotos.length && <div className="care-entry-photos">{existingPhotos.filter(p => p.hasData).map(p => <div key={p.id}><CarePhoto id={p.id} /><Button disabled={busy} onClick={async () => { if (!window.confirm(t('Удалить эту фотографию?'))) return; setBusy(true); try { await careRequest(`/photos/${p.id}`, 'DELETE'); setExistingPhotos(old => old.filter(photo => photo.id !== p.id)); onSaved(); } catch (e) { setError(e.message); } finally { setBusy(false); } }}>{t('Удалить фото')}</Button></div>)}</div>}
    <small>{t('Фото останутся приватными. Можно выбрать снимок из галереи или сделать новый.')}</small>
    {form.action === 'watering' && <p>{t('Это запись о ручном уходе. Устройства не включатся.')}</p>}
  </form></Modal>;
}
