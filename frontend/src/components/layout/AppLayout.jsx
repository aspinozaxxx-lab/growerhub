import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import './AppLayout.css';

const navItems = [
  { to: '/app', label: 'Dashboard', icon: '🏠', end: true },
  { to: '/app/plants', label: 'Plants', icon: '🌱' },
  { to: '/app/devices', label: 'Devices', icon: '🔧' },
  { to: '/app/profile', label: 'Profile', icon: '👤' },
];

// Funkciya otobrazheniya punktov menyu, ispolzuetsya i v sidebar, i v nizhnej paneli
const renderNavItems = () =>
  navItems.map((item) => (
    <NavLink
      key={item.to}
      to={item.to}
      end={item.end}
      className={({ isActive }) => (isActive ? 'app-nav__item is-active' : 'app-nav__item')}
    >
      <span className="app-nav__icon" aria-hidden="true">{item.icon}</span>
      <span className="app-nav__label">{item.label}</span>
    </NavLink>
  ));

// Layout dlya kabineta: mobilnaya nizhnyaya panel, na desktop - levaya kolonka
function AppLayout() {
  return (
    <div className="app-layout">
      <aside className="app-sidebar">
        <div className="app-sidebar__inner">
          <div className="app-sidebar__brand">GrowerHub</div>
          <nav className="app-nav app-nav--sidebar">
            {renderNavItems()}
          </nav>
        </div>
      </aside>

      <main className="app-content">
        <div className="app-content__inner">
          <Outlet />
        </div>
      </main>

      <div className="app-nav-shell app-nav-shell--bottom">
        <nav className="app-nav app-nav--bottom" aria-label="Navigation">
          {renderNavItems()}
        </nav>
      </div>
    </div>
  );
}

export default AppLayout;
