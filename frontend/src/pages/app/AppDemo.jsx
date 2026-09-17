import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Leaf } from 'lucide-react';
import { useAuth } from '../../features/auth/AuthContext';
import { getCurrentLocale, rememberLocale, translateApp as t } from '../../locales/i18n';
import './AppDemo.css';
import { trackProductGoal } from '../../utils/analytics';

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
  const save = new URLSearchParams(location.search).get('save') === '1';
  const expired = new URLSearchParams(location.search).get('expired') === '1';
  const requestedView = new URLSearchParams(location.search).get('view');
  const view = Object.hasOwn(DEMO_VIEWS, requestedView) ? requestedView : 'overview';

  const run = async (operation) => {
    if (busy) return;
    setBusy(true); setFailure(null);
    rememberLocale(getCurrentLocale());
    try {
      const result = await operation();
      if (result.success) navigate(DEMO_VIEWS[view], { replace: true });
      else setFailure(result.status);
    } catch (error) {
      if (error?.name !== 'AbortError') setFailure(503);
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
    run(save ? () => auth.saveDemo(false) : auth.startDemo);
    // Translitem: vhod vypolnjaetsja odin raz na otkrytie marshruta.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.accountStatus, save, expired, navigate]);

  return (
    <div className="demo-entry">
      <div className="demo-entry__card" aria-busy={busy}>
        <Leaf size={36} aria-hidden="true" />
        <p className="demo-eyebrow">GrowerHub · {t('Демоферма')}</p>
        <h1>{failure === 409 ? t('У вас уже есть сохранённая демоферма') : t('Попробуйте управление фермой')}</h1>
        <p>{t('Четыре теплицы, растения, полив, свет и история датчиков. Можно менять настройки и добавлять виртуальные устройства.')}</p>
        {busy || ['idle', 'loading'].includes(auth.accountStatus) ? <p role="status">{t('Подготавливаем вашу демоферму с историей…')}</p> : null}
        {failure === 409 ? (
          <div className="demo-actions">
            <button className="gh-btn gh-btn--primary gh-btn--md" disabled={busy} onClick={() => run(auth.startDemo)}>{t('Открыть сохранённую')}</button>
            <button className="gh-btn gh-btn--outline gh-btn--md" disabled={busy} onClick={() => run(() => auth.saveDemo(true))}>{t('Заменить её текущей демофермой')}</button>
            <p>{t('При замене настройки предыдущей демофермы будут удалены.')}</p>
          </div>
        ) : null}
        {failure && failure !== 409 ? <p role="alert">{failure === 429 ? t('Сейчас демоферма занята. Попробуйте немного позже.') : t('Не удалось открыть демоферму. Попробуйте ещё раз.')}</p> : null}
        {expired ? <p role="status">{t('Сессия демо завершилась. Откройте демоферму снова, чтобы продолжить.')}</p> : null}
        {!busy && (expired || (failure && failure !== 409)) ? <button className="gh-btn gh-btn--primary gh-btn--md" onClick={() => run(save ? () => auth.saveDemo(false) : auth.startDemo)}>{t('Открыть демоферму')}</button> : null}
        <Link to="/app/" onClick={auth.leaveDemo}>{t('Вернуться в приложение')}</Link>
      </div>
    </div>
  );
}
