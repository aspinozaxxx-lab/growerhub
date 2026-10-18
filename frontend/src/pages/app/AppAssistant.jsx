import { Link, useLocation } from 'react-router-dom';
import AssistantChat from '../../features/assistant/AssistantChat';
import { translateApp as t } from '../../locales/i18n';
export default function AppAssistant() {
  const location = useLocation();
  return <div className="assistant-page"><Link to="/app/">← {t('Обзор')}</Link><h1>{t('Помощник GrowerHub')}</h1><AssistantChat initialText={location.state?.prompt || ''} /></div>;
}
