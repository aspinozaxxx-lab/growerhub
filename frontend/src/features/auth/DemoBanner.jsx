import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { FlaskConical, RotateCcw } from 'lucide-react';
import { useAuth } from './AuthContext';
import { translateApp as t } from '../../locales/i18n';
import { trackProductGoal } from '../../utils/analytics';
import '../../pages/app/AppDemo.css';

export default function DemoBanner() {
  const { demoActive, demoSession, accountUser, leaveDemo, resetDemo } = useAuth();
  const navigate = useNavigate();
  const [confirmReset, setConfirmReset] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (!demoActive) return null;
  const connect = () => {
    trackProductGoal('demo_real_setup_start', { placement: 'demo_banner' });
    leaveDemo(); navigate('/app/onboarding/');
  };
  const reset = async () => {
    setBusy(true); setError('');
    try { if (!(await resetDemo()).success) setError(t('Не удалось восстановить демоферму')); }
    catch { setError(t('Не удалось восстановить демоферму')); }
    finally { setBusy(false); setConfirmReset(false); }
  };
  return (
    <aside className="demo-banner" aria-label={t('Демонстрационный режим')}>
      <div className="demo-banner__intro">
        <FlaskConical size={23} aria-hidden="true" />
        <div><strong>{t('Демо · виртуальные устройства')}</strong>
          <p>{demoSession?.saved ? t('Изменения сохраняются в вашем аккаунте.') : t('Изменения доступны 24 часа. Сохраните демоферму в аккаунт, чтобы продолжить позже.')}</p>
        </div>
      </div>
      <div className="demo-actions">
        <Link className="demo-link" to="/app/demo-tools/">{t('Устройства и условия среды')}</Link>
        {!demoSession?.saved ? <Link className="demo-link demo-link--primary" to="/app/demo/?save=1">{t('Сохранить демоферму')}</Link> : null}
        <button className="demo-link" onClick={connect}>{t('Подключить свои устройства')}</button>
        {accountUser ? <button className="demo-link" onClick={() => { trackProductGoal('demo_exit'); leaveDemo(); navigate('/app/'); }}>{t('Моя ферма')}</button> : null}
        <button className="demo-link demo-reset" onClick={() => setConfirmReset(true)} aria-label={t('Восстановить демоферму')}><RotateCcw size={16} /><span>{t('Сбросить демо')}</span></button>
      </div>
      {confirmReset ? <div className="demo-confirm" role="group" aria-label={t('Подтверждение сброса')}>
        <p>{t('Ваши изменения в демо будут удалены. Восстановить исходную демоферму с историей?')}</p>
        <button className="gh-btn gh-btn--primary gh-btn--sm" disabled={busy} onClick={reset}>{busy ? t('Восстанавливаем…') : t('Восстановить демоферму')}</button>
        <button className="gh-btn gh-btn--outline gh-btn--sm" disabled={busy} onClick={() => setConfirmReset(false)}>{t('Отмена')}</button>
      </div> : null}
      {error ? <p role="alert">{error}</p> : null}
    </aside>
  );
}
