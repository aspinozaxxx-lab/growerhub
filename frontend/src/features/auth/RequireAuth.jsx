import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { translateApp } from '../../locales/i18n';

function RequireAuth({ children }) {
  const { status, demoActive } = useAuth();
  const location = useLocation();

  if (status === 'loading' || status === 'idle') {
    return <div className="app-loading">{translateApp("Загрузка...")}</div>;
  }

  if (status === 'unauthorized') {
    const returnPath = location.pathname + location.search;
    return <Navigate to={demoActive ? "/app/demo/?expired=1" : `/app/login/?redirect=${encodeURIComponent(returnPath)}`} replace />;
  }

  return children;
}

export default RequireAuth;
