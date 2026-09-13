import React, { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { translateApp } from '../../locales/i18n';

function RequireAuth({ children }) {
  const { status, demoActive, setRedirectAfterLogin } = useAuth();
  const location = useLocation();

  useEffect(() => {
    if (!demoActive && status === 'unauthorized' && location.pathname !== '/app/login/') {
      setRedirectAfterLogin(location.pathname);
    }
  }, [status, demoActive, location.pathname, setRedirectAfterLogin]);

  if (status === 'loading' || status === 'idle') {
    return <div className="app-loading">{translateApp("Загрузка...")}</div>;
  }

  if (status === 'unauthorized') {
    return <Navigate to={demoActive ? "/app/demo/?expired=1" : "/app/login/"} replace />;
  }

  return children;
}

export default RequireAuth;
