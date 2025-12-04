import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import './AppProfile.css';

function AppProfile() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/app/login', { replace: true });
  };

  if (!user) {
    return <div className="profile-card">Нет данных профиля</div>;
  }

  return (
    <div className="app-profile">
      <h2>Профиль</h2>
      <div className="profile-card">
        <div className="profile-row">
          <span className="profile-label">ID</span>
          <span>{user.id}</span>
        </div>
        <div className="profile-row">
          <span className="profile-label">Email</span>
          <span>{user.email || '—'}</span>
        </div>
        <div className="profile-row">
          <span className="profile-label">Имя пользователя</span>
          <span>{user.username || '—'}</span>
        </div>
        <div className="profile-row">
          <span className="profile-label">Роль</span>
          <span>{user.role || '—'}</span>
        </div>
        <div className="profile-row">
          <span className="profile-label">Статус</span>
          <span>{user.is_active ? 'Активен' : 'Неактивен'}</span>
        </div>
      </div>

      <button type="button" className="logout-button" onClick={handleLogout}>
        Выйти
      </button>
    </div>
  );
}

export default AppProfile;
