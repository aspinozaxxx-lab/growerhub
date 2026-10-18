import { useEffect, useState } from 'react';
import { assistantRequest } from '../../api/assistant';
import { translateApp as t } from '../../locales/i18n';

export default function CodexStatus() {
  const [state, setState] = useState(null);
  useEffect(() => { let live = true; assistantRequest('/runtime').then(value => { if (live) setState(value); }).catch(() => {}); return () => { live = false; }; }, []);
  if (!state) return null;
  return <section className="care-panel" aria-label={t('Обновления Codex')}>
    <h2>{t('Обновления Codex')}</h2>
    <p>{t('Установлена версия')}: {state.installed || '—'} · {t('Последняя стабильная')}: {state.latest || '—'}</p>
    <p role="status">{t(state.error ? 'Проверка обновлений временно недоступна.' : state.updateAvailable ? 'Доступно обновление серверного Codex.' : 'Установленная версия Codex актуальна.')}</p>
    {state.checkedAt && <small>{t('Последняя проверка')}: {new Date(state.checkedAt).toLocaleString()}</small>}
    <p>{t('Проверяем каждые 6 часов. О новой версии сообщим только владельцу в Telegram. Обновление выполняется вручную, с проверкой и возможностью отката.')}</p>
  </section>;
}
