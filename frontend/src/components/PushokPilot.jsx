import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { fetchPushokPilot, savePushokPilot, withdrawPushokPilot } from '../api/pushokPilot';
import { getPublicPath } from '../domain/localizedRoutes';
import { getCurrentLocale, translateApp as t } from '../locales/i18n';
import Button from './ui/Button';
import FormField from './ui/FormField';
import Modal from './ui/Modal';
import './PushokPilot.css';

function PushokPilot() {
  const [params, setParams] = useSearchParams();
  const [open, setOpen] = useState(() => params.get('pilot') === 'pushok');
  const [step, setStep] = useState('intent');
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(null);
  const [form, setForm] = useState({ contact_method: 'TELEGRAM', contact: '', equipment: '' });

  useEffect(() => {
    if (!open) return undefined;
    let active = true;
    setLoading(true);
    setLoaded(false);
    setError('');
    fetchPushokPilot().then(({ request }) => {
      if (!active) return;
      setSaved(request);
      setStep(request ? 'sent' : 'intent');
      if (request) setForm({ contact_method: request.contact_method, contact: request.contact, equipment: request.equipment || '' });
      setLoaded(true);
    }).catch(() => {
      if (active) setError(t('Не удалось проверить заявку. Попробуйте ещё раз.'));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, retry]);

  const close = () => {
    if (busy) return;
    setOpen(false);
    if (params.get('pilot') === 'pushok') {
      const next = new URLSearchParams(params);
      next.delete('pilot');
      setParams(next, { replace: true });
    }
  };
  const change = (key, value) => setForm((current) => ({ ...current, [key]: value }));
  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const { request } = await savePushokPilot({ ...form, contact: form.contact.trim(), equipment: form.equipment.trim() });
      if (!request?.requested_at) throw new Error('Pilot request was not confirmed');
      setSaved(request);
      setStep('sent');
    } catch {
      setError(t('Не удалось отправить заявку. Проверьте контакт и попробуйте ещё раз.'));
    } finally { setBusy(false); }
  };
  const withdraw = async () => {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await withdrawPushokPilot();
      setSaved(null);
      setForm({ contact_method: 'TELEGRAM', contact: '', equipment: '' });
      setStep('withdrawn');
    } catch { setError(t('Не удалось отозвать заявку. Попробуйте ещё раз.')); }
    finally { setBusy(false); }
  };

  return (
    <div className="pushok-pilot">
      <div className="pushok-pilot__entry">
        <Button onClick={() => setOpen(true)}>{t('Подключить ПушОк')}</Button>
        <span>{t('Набираем участников пилота')}</span>
      </div>
      <Modal isOpen={open} title={t('Пилот подключения ПушОк')} onClose={close} presentation="sheet" size="sm" disableOverlayClose={busy}>
        {loading ? <p role="status">{t('Проверяем заявку…')}</p> : null}
        {error ? <p role="alert">{error}</p> : null}
        {!loading && !loaded ? <Button onClick={() => setRetry((value) => value + 1)}>{t('Повторить')}</Button> : null}
        {!loading && loaded && step === 'intent' ? <>
          <p>{t('Хотите подключить свой ПушОк к GrowerHub или пока просто смотрите?')}</p>
          <p className="pushok-pilot__hint">{t('Интеграция ещё не запущена. Ищем первых участников, чтобы вместе отработать подключение. Заявка не меняет настройки хаба.')}</p>
          <div className="pushok-pilot__actions">
            <Button variant="primary" autoFocus onClick={() => setStep('form')}>{t('Хочу участвовать')}</Button>
            <Button onClick={close}>{t('Просто смотрю')}</Button>
          </div>
        </> : null}
        {!loading && loaded && step === 'form' ? <form className="pushok-pilot__form ym-hide-content" onSubmit={submit}>
          <p>{t('Оставьте удобный способ связи. Свяжемся с вами в ближайшее время и обсудим пилотное подключение.')}</p>
          <FormField label={t('Как с вами связаться')} htmlFor="pushok-contact-method">
            <select id="pushok-contact-method" autoFocus value={form.contact_method} onChange={(event) => change('contact_method', event.target.value)} disabled={busy}>
              <option value="TELEGRAM">Telegram</option>
              <option value="EMAIL">Email</option>
              <option value="OTHER">{t('Другой способ')}</option>
            </select>
          </FormField>
          <FormField label={t('Контакт для ответа')} htmlFor="pushok-contact" hint={form.contact_method === 'TELEGRAM' ? t('Ваш @username или ссылка t.me. Телефон можно указать в «Другой способ».') : undefined}>
            <input id="pushok-contact" className="ym-disable-keys" type={form.contact_method === 'EMAIL' ? 'email' : 'text'} required maxLength={254}
              pattern={form.contact_method === 'TELEGRAM' ? '(?:@|https://t\\.me/)?[A-Za-z0-9_]{5,32}/?' : undefined}
              autoComplete={form.contact_method === 'EMAIL' ? 'email' : 'off'}
              value={form.contact} onChange={(event) => change('contact', event.target.value)} disabled={busy} />
          </FormField>
          <FormField label={t('Какое оборудование есть (необязательно)')} htmlFor="pushok-equipment" hint={t('Модель хаба и датчиков. Если оборудования пока нет, так и напишите.')}>
            <textarea id="pushok-equipment" className="ym-disable-keys" rows={2} maxLength={500} value={form.equipment} onChange={(event) => change('equipment', event.target.value)} disabled={busy} />
          </FormField>
          <p className="pushok-pilot__hint">{t('Отправляя заявку, вы просите связаться с вами по поводу пилота. Контакт увидит только команда GrowerHub. Заявку можно отозвать здесь же.')}{' '}
            <Link to={getPublicPath('privacy', getCurrentLocale())} target="_blank" rel="noreferrer">{t('Политика конфиденциальности')}</Link>
          </p>
          <Button type="submit" variant="primary" isLoading={busy} disabled={!form.contact.trim()}>{t(saved ? 'Обновить заявку' : 'Отправить заявку')}</Button>
        </form> : null}
        {!loading && loaded && step === 'sent' ? <>
          <p role="status"><strong>{t('Заявка сохранена')}</strong></p>
          <p>{t('Спасибо! Свяжемся с вами в ближайшее время, чтобы вместе отработать пилотное подключение ПушОк.')}</p>
          <p className="pushok-pilot__contact ym-hide-content">{saved?.contact_method === 'OTHER' ? t('Другой способ') : saved?.contact_method === 'EMAIL' ? 'Email' : 'Telegram'}: {saved?.contact}</p>
          <div className="pushok-pilot__actions">
            <Button autoFocus onClick={() => { setError(''); setStep('form'); }} disabled={busy}>{t('Изменить заявку')}</Button>
            <Button variant="ghost" onClick={withdraw} isLoading={busy}>{t('Отозвать заявку')}</Button>
          </div>
        </> : null}
        {!loading && loaded && step === 'withdrawn' ? <p role="status">{t('Заявка отозвана, контакт удалён из списка пилота.')}</p> : null}
      </Modal>
    </div>
  );
}

export default PushokPilot;
