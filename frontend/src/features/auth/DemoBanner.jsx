import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChevronDown, FlaskConical, MessageCircle, RotateCcw } from 'lucide-react';
import Modal from '../../components/ui/Modal';
import TelegramContactLink from '../../components/TelegramContactLink';
import useCompactLayout from '../../components/layout/useCompactLayout';
import { useAuth } from './AuthContext';
import { translateApp as t } from '../../locales/i18n';
import { trackProductGoal } from '../../utils/analytics';
import '../../pages/app/AppDemo.css';

export default function DemoBanner() {
  const compact = useCompactLayout();
  const [expanded, setExpanded] = useState(false);
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
  const content = <>
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
        <TelegramContactLink className="demo-link" placement={compact ? 'demo_menu_feedback' : 'demo_banner_feedback'}><MessageCircle size={16} aria-hidden="true" />{t('Вопрос или отзыв в Telegram')}</TelegramContactLink>
        <button className="demo-link demo-reset" onClick={() => setConfirmReset(true)} aria-label={t('Восстановить демоферму')}><RotateCcw size={16} /><span>{t('Сбросить демо')}</span></button>
      </div>
      {confirmReset ? <div className="demo-confirm" role="group" aria-label={t('Подтверждение сброса')}>
        <p>{t('Ваши изменения в демо будут удалены. Восстановить исходную демоферму с историей?')}</p>
        <button className="gh-btn gh-btn--primary gh-btn--sm" disabled={busy} onClick={reset}>{busy ? t('Восстанавливаем…') : t('Восстановить демоферму')}</button>
        <button className="gh-btn gh-btn--outline gh-btn--sm" disabled={busy} onClick={() => setConfirmReset(false)}>{t('Отмена')}</button>
      </div> : null}
      {error ? <p role="alert">{error}</p> : null}
  </>;
  return <aside className="demo-banner" aria-label={t('Демонстрационный режим')}>
    {compact ? <>
      <div className="demo-banner__compact-row">
        <button type="button" className="demo-banner__compact" onClick={() => setExpanded(true)} aria-expanded={expanded} aria-label={`${t('Демо')}. ${demoSession?.saved ? t('Сохранено в аккаунте') : t('Изменения доступны 24 ч')}`}>
          <FlaskConical size={17} aria-hidden="true" /><strong>{t('Демо')}</strong>
          <span>{demoSession?.saved ? t('Сохранено') : <>24 {t('ч')}</>}</span>
          <ChevronDown size={16} aria-hidden="true" />
        </button>
        <TelegramContactLink className="demo-banner__feedback" placement="demo_banner_feedback"><MessageCircle size={16} aria-hidden="true" />{t('Отзыв в Telegram')}</TelegramContactLink>
      </div>
      <Modal isOpen={expanded} title={t('Демонстрационный режим')} presentation="sheet" onClose={() => { if (!busy) setExpanded(false); }}>
        {content}
      </Modal>
    </> : content}
  </aside>;
}
