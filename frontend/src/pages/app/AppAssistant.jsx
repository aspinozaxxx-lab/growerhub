import { useLocation } from 'react-router-dom';
import AssistantChat from '../../features/assistant/AssistantChat';
export default function AppAssistant() {
  const location = useLocation();
  return <div className="assistant-page"><AssistantChat initialText={location.state?.prompt || ''} backTo="/app/" /></div>;
}
