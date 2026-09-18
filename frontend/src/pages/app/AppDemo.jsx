import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Leaf } from 'lucide-react';
import { useAuth } from '../../features/auth/AuthContext';
import { getCurrentLocale, rememberLocale, translateApp as t } from '../../locales/i18n';
import './AppDemo.css';
import { trackProductGoal } from '../../utils/analytics';
import { isSessionExpiredError } from '../../api/client';
import useSeoMeta from '../../utils/useSeoMeta';

const DEMO_VIEWS = {
  overview: '/app/',
  automations: '/app/automations/',
  watering: '/app/manual-watering/',
  farm: '/app/farm/',
  plants: '/app/plants/',
};

export default function AppDemo() {
  const auth = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const started = useRef(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState(null);
  const [saveUnavailable, setSaveUnavailable] = useState(false);
  const save = new URLSearchParams(location.search).get('save') === '1';
  const expired = new URLSearchParams(location.search).get('expired') === '1';
  const requestedView = new URLSearchParams(location.search).get('view');
  const view = Object.hasOwn(DEMO_VIEWS, requestedView) ? requestedView : 'overview';
  const description = t('Четыре теплицы, растения, полив, свет и история датчиков. Можно менять настройки и добавлять виртуальные устройства.');

  useSeoMeta({
    title: `${t('Демоферма')} — GrowerHub`,
    description,
    path: null,
    robots: 'noindex,nofollow',
  });

  const run = async (operation, saving = false) => {
    if (busy) return;
    setBusy(true); setFailure(null);
    rememberLocale(getCurrentLocale());
    try {
      const result = await operation();
      if (result.success) navigate(DEMO_VIEWS[view], { replace: true });
      else {
        setFailure(result.status);
        if (saving && result.status === 410) setSaveUnavailable(true);
      }
    } catch (error) {
      if (isSessionExpiredError(error)) {
        const resume = saving ? '/app/demo/?save=1' : '/app/demo/?view=' + view;
        navigate('/app/login/?redirect=' + encodeURIComponent(resume), { replace: true });
      } else if (error?.name !== 'AbortError') setFailure(503);
    } finally { setBusy(false); }
  };

  useEffect(() => {
    if (started.current || ['idle', 'loading'].includes(auth.accountStatus)) return;
    if (save && auth.accountStatus !== 'authorized') {
      started.current = true;
      navigate('/app/login/?redirect=' + encodeURIComponent('/app/demo/?save=1'), { replace: true });
      return;
    }
    if (expired) return;
    started.current = true;
    if (!save) trackProductGoal('demo_open', { placement: location.state?.demoPlacement || 'direct', action: view });
    run(save ? () => auth.saveDemo(false) : auth.startDemo, save);
    // Translitem: vhod vypolnjaetsja odin raz na otkrytie marshruta.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.accountStatus, save, expired, navigate]);

  return (
    <div className="demo-entry">
      <div className="demo-entry__card" aria-busy={busy}>
        <Leaf size={36} aria-hidden="true" />
        <p className="demo-eyebrow">GrowerHub · {t('Демоферма')}</p>
        <h1>{failure === 409 ? t('У вас уже есть сохранённая демоферма') : t('Попробуйте управление фермой')}</h1>
        <p>{description}</p>
        {busy || ['idle', 'loading'].includes(auth.accountStatus) ? <p role="status">{t('Подготавливаем вашу демоферму с историей…')}</p> : null}
        {failure === 409 ? (
          <div className="demo-actions">
            <button className="gh-btn gh-btn--primary gh-btn--md" disabled={busy} onClick={() => run(auth.startDemo)}>{t('Открыть сохранённую')}</button>
            <button className="gh-btn gh-btn--outline gh-btn--md" disabled={busy} onClick={() => run(() => auth.saveDemo(true), true)}>{t('Заменить её текущей демофермой')}</button>
            <p>{t('При замене настройки предыдущей демофермы будут удалены.')}</p>
          </div>
        ) : null}
        {failure && failure !== 409 ? <p role="alert">{failure === 410 ? t('Текущая демосессия недоступна. Сохранить её изменения не удалось.') : failure === 429 ? t('Сейчас демоферма занята. Попробуйте немного позже.') : t('Не удалось открыть демоферму. Попробуйте ещё раз.')}</p> : null}
        {saveUnavailable ? <p>{t('Откроется сохранённая демоферма вашего аккаунта. Если её нет, будет создана новая.')}</p> : null}
        {expired ? <p role="status">{t('Сессия демо завершилась. Откройте демоферму снова, чтобы продолжить.')}</p> : null}
        {!busy && (expired || (failure && failure !== 409)) ? <button className="gh-btn gh-btn--primary gh-btn--md" onClick={() => run(save && !saveUnavailable ? () => auth.saveDemo(false) : auth.startDemo, save && !saveUnavailable)}>{t('Открыть демоферму')}</button> : null}
        <Link to="/app/" onClick={auth.leaveDemo}>{t('Вернуться в приложение')}</Link>
      </div>
    </div>
  );
}
