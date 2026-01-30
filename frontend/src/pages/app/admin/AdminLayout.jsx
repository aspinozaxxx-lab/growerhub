import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import Surface from '../../../components/ui/Surface';
import './AdminLayout.css';
import './AdminPages.css';

// Translitem: punkty navigacii admin-razdela.
const adminNavItems = [
  { to: '/app/admin/users', label: 'Пользователи' },
  { to: '/app/admin/devices', label: 'Устройства' },
  { to: '/app/admin/plants', label: 'Растения' },
];

// Translitem: obshchiy layout dlya admin-stranic.
function AdminLayout() {
  return (
    <div className="admin-layout">
      <AppPageHeader
        title="Администрирование"
        right={(
          <NavLink to="/app/profile" className="admin-nav__back">
            Назад в профиль
          </NavLink>
        )}
      />

      <Surface variant="card" padding="md" className="admin-layout__surface">
        <nav className="admin-nav">
          {adminNavItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? 'admin-nav__link is-active' : 'admin-nav__link')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </Surface>

      <div className="admin-layout__content">
        <Outlet />
      </div>
    </div>
  );
}

export default AdminLayout;
