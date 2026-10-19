import { lazy, Suspense, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { MessageCircle } from 'lucide-react';
import Modal from '../../components/ui/Modal';
import useCompactLayout from '../../components/layout/useCompactLayout';
import { translateApp as t } from '../../locales/i18n';
import './assistant.css';
const Chat = lazy(() => import('./AssistantChat'));
export default function AssistantEntry() {
  const compact = useCompactLayout(); const [open, setOpen] = useState(false); const location = useLocation();
  if (location.pathname.startsWith('/app/assistant/')) return null;
  const label = <><MessageCircle size={20} aria-hidden="true" /><span>{t('Помощник')}</span></>;
  return <>{compact ? <Link to="/app/assistant/" className="assistant-launch">{label}</Link> : <button type="button" className="assistant-launch" onClick={() => setOpen(true)}>{label}</button>}
    {open && <div className="assistant-dialog"><Modal isOpen title={t('Помощник GrowerHub')} showHeader={false} onClose={() => setOpen(false)}><Suspense fallback={<p>{t('Загрузка…')}</p>}><Chat onClose={() => setOpen(false)} /></Suspense></Modal></div>}
  </>;
}
