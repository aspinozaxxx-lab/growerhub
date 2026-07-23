import React, { useEffect } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import './AppLayout.css';
import {
  getCurrentLocale,
  getStoredLocale,
  rememberLocale,
  translateApp,
} from '../../locales/i18n';
import { translateCommon } from '../../locales/i18n';

const navItems = [
  { to: '/app/', label: 'Обзор', icon: '⌂', end: true },
  { to: '/app/connections/', label: 'Подключения', icon: '⇄' },
  { to: '/app/zones/', label: 'Зоны', icon: '▦' },
  { to: '/app/devices/', label: 'Устройства', icon: '◉' },
  { to: '/app/automations/', label: 'Автоматизации', icon: '⚡' },
  { to: '/app/profile/', label: 'Профиль', icon: '○' },
  { to: '/app/plants/', label: 'Растения', icon: '♧' },
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
      <span className="app-nav__label">{translateApp(item.label)}</span>
    </NavLink>
  ));

// Layout dlya kabineta: mobilnaya nizhnyaya panel, na desktop - levaya kolonka
function AppLayout() {
  const location = useLocation();
  const adminRoute = location.pathname.startsWith('/app/admin/');
  const currentLocale = getCurrentLocale();

  useEffect(() => {
    const desiredLocale = adminRoute ? 'ru' : getStoredLocale();
    if (currentLocale === desiredLocale) return;
    const target = new URL(window.location.href);
    target.searchParams.set('lang', desiredLocale);
    window.location.replace(`${target.pathname}${target.search}${target.hash}`);
  }, [adminRoute, currentLocale]);

  const switchLocale = () => {
    const nextLocale = currentLocale === 'ru' ? 'en' : 'ru';
    rememberLocale(nextLocale);
    const target = new URL(window.location.href);
    target.searchParams.set('lang', nextLocale);
    window.location.assign(`${target.pathname}${target.search}${target.hash}`);
  };

  return (
    <div className="app-layout">
      <aside className="app-sidebar">
        <div className="app-sidebar__inner">
          <div className="app-sidebar__brand">GrowerHub</div>
          {!adminRoute ? (
            <button
              type="button"
              className="app-locale-switch"
              onClick={switchLocale}
              aria-label={translateCommon('language.switch')}
            >
              {currentLocale === 'ru' ? 'EN' : 'RU'}
            </button>
          ) : null}
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
        <nav className="app-nav app-nav--bottom" aria-label={translateApp("Навигация кабинета")}>
          {renderNavItems()}
        </nav>
      </div>
    </div>
  );
}

export default AppLayout;
