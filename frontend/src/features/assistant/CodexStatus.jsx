import { useEffect, useRef, useState } from 'react';
import { assistantRequest } from '../../api/assistant';
import { translateApp as t } from '../../locales/i18n';
import Button from '../../components/ui/Button';
import './assistant.css';

const phases = {
  queued: 'Обновление принято', installing: 'Загружаем выбранную версию', checking: 'Проверяем совместимость',
  waiting: 'Ждём завершения текущего ответа', switching: 'Переключаем версию', verifying: 'Проверяем ответ помощника',
  rolling_back: 'Возвращаем предыдущую версию', succeeded: 'Обновление завершено, ответ помощника проверен',
  rolled_back: 'Восстановлена предыдущая версия, проверка пройдена', failed: 'Обновление остановлено до переключения',
  rollback_failed: 'Откат не прошёл проверку — требуется проверка на сервере',
};

export default function CodexStatus() {
  const [state, setState] = useState(null); const [maintenance, setMaintenance] = useState(null);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [confirm, setConfirm] = useState(null);
  const pending = useRef(null); const live = useRef(true);
  const operation = maintenance?.operation; const active = operation && !operation.finishedAt;
  const load = async () => {
    const [status, update] = await Promise.all([assistantRequest('/runtime'), assistantRequest('/runtime/update')]);
    if (live.current) {
      setState(status); setMaintenance(update);
      if (pending.current && update.operation?.id === pending.current.id) { pending.current = null; setConfirm(null); }
    }
  };
  useEffect(() => {
    live.current = true;
    load().catch(() => {});
    const timer = setInterval(() => { if (document.visibilityState === 'visible') load().catch(() => {}); }, 5000);
    return () => { live.current = false; clearInterval(timer); };
  }, []);
  if (!state) return null;
  const check = async () => {
    setBusy(true); setError('');
    try { setState(await assistantRequest('/runtime/check', 'POST')); await load(); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  const start = async () => {
    if (busy) return;
    setBusy(true); setError('');
    pending.current ||= { id: crypto.randomUUID(), target: confirm.latest, expectedInstalled: confirm.installed };
    try {
      const result = await assistantRequest('/runtime/update', 'POST', pending.current);
      setMaintenance(old => ({ ...old, operation: result })); setConfirm(null); pending.current = null;
    } catch (e) { setError(e.message); await load().catch(() => {}); }
    finally { setBusy(false); }
  };
  return <section className="care-panel codex-status" aria-label={t('Обновления Codex')}>
    <h2>{t('Обновления Codex')}</h2>
    <p>{t('Установлена версия')}: {state.installed || '—'} · {t('Последняя стабильная')}: {state.latest || '—'}</p>
    <p role="status">{t(state.error ? 'Проверка обновлений временно недоступна.' : state.updateAvailable ? 'Доступно обновление серверного Codex.' : 'Установленная версия Codex актуальна.')}</p>
    {state.checkedAt && <small>{t('Последняя проверка')}: {new Date(state.checkedAt).toLocaleString()}</small>}
    <p>{t('Проверяем каждые 6 часов. О новой версии и результате обновления сообщим только владельцу в Telegram.')}</p>
    <div className="assistant-actions"><Button disabled={busy || active} onClick={check}>{t('Проверить обновления')}</Button>
      {maintenance?.enabled && <Button variant="primary" disabled={busy || active || !state.updateAvailable || !!state.error} onClick={() => { pending.current = null; setConfirm(state); }}>{t('Обновить Codex')}</Button>}
    </div>
    {confirm && !active && <section className="assistant-settings" role="alertdialog" aria-label={t('Подтвердить обновление Codex')}>
      <strong>{confirm.installed} → {confirm.latest}</strong>
      <p>{t('Проверим новую версию, дождёмся текущего ответа и переключим личного помощника. Если проверка ответа не пройдёт, вернём предыдущую версию. Страницу можно закрыть.')}</p>
      <div className="assistant-actions"><Button variant="primary" disabled={busy} onClick={start}>{t('Запустить обновление')}</Button><Button disabled={busy} onClick={() => { setConfirm(null); pending.current = null; }}>{t('Отмена')}</Button></div>
    </section>}
    {operation && <div className="assistant-settings" aria-live="polite">
      <strong>{t(phases[operation.phase] || 'Проверяем состояние операции')}</strong>
      <p>{operation.previous} → {operation.target}{operation.installed && <> · {t('Установлена версия')}: {operation.installed}</>}</p>
      {operation.phase === 'failed' && <p>{t('Предыдущая версия сохранена. Завершите текущий разговор и повторите проверку обновлений.')}</p>}
      {operation.updatedAt && <small>{t('Последнее состояние')}: {new Date(operation.updatedAt).toLocaleString()}</small>}
      {active && Date.now() - new Date(operation.updatedAt).getTime() > 120000 && <p>{t('Ждём подтверждения от сервера. Повторный запуск не нужен.')}</p>}
    </div>}
    {error && <p role="alert">{error}</p>}
  </section>;
}
