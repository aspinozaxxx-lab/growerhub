import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';

// Translitem: guard dlya admin-marshrutov.
function RequireAdmin({ children }) {
  const { status, user } = useAuth();

  if (status === 'loading' || status === 'idle') {
    return <div className="app-loading">Загрузка...</div>;
  }

  if (status !== 'authorized') {
    return <Navigate to="/app/profile" replace />;
  }

  if (!user || user.role !== 'admin') {
    return <Navigate to="/app/profile" replace />;
  }

  return children;
}

export default RequireAdmin;
