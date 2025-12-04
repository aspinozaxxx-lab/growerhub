import React, { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

function RequireAuth({ children }) {
  const { status, setRedirectAfterLogin } = useAuth();
  const location = useLocation();

  useEffect(() => {
    if (status === 'unauthorized' && location.pathname !== '/app/login') {
      setRedirectAfterLogin(location.pathname);
    }
  }, [status, location.pathname, setRedirectAfterLogin]);

  if (status === 'loading' || status === 'idle') {
    return <div className="app-loading">Загрузка...</div>;
  }

  if (status === 'unauthorized') {
    return <Navigate to="/app/login" replace />;
  }

  return children;
}

export default RequireAuth;
