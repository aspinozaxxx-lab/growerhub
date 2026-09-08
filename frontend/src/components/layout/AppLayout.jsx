import React, { useEffect } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { Leaf } from 'lucide-react';
import { getPublicPath } from '../../domain/localizedRoutes';
import './AppLayout.css';
import {
  getCurrentLocale,
  getStoredLocale,
  rememberLocale,
  translateApp,
} from '../../locales/i18n';
import { translateCommon } from '../../locales/i18n';
import { APP_NAV_ITEMS } from '../../pages/app/appNavigation';

// Edinoe menyu dlya verhnej, bokovoj i mobilnoj navigacii.
const renderNavItems = () =>
  APP_NAV_ITEMS.map((item) => (
    <NavLink
      key={item.to}
      to={item.to}
      end={item.end}
      aria-label={translateApp(item.label)}
      className={({ isActive }) => (isActive ? 'app-nav__item is-active' : 'app-nav__item')}
    >
      <span className="app-nav__icon" aria-hidden="true">{React.createElement(item.icon, { size: 19, strokeWidth: 1.7 })}</span>
      <span className="app-nav__label">{translateApp(item.label)}</span>
      <span className="app-nav__short-label">{translateApp(item.shortLabel || item.label)}</span>
    </NavLink>
  ));

// Kabinet ispolzuet verhnee menyu; admin sohranyaet bokovuyu navigaciyu.
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
    <div className={`app-layout ${adminRoute ? 'app-layout--admin' : 'app-layout--workspace'}`}>
      <aside className="app-sidebar">
        <div className="app-sidebar__inner">
          <Link to={getPublicPath('home', currentLocale)} className="app-sidebar__brand">
            <Leaf size={28} strokeWidth={1.6} aria-hidden="true" />
            <span>GrowerHub</span>
          </Link>
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
          <nav className="app-nav app-nav--sidebar" aria-label={translateApp("Навигация кабинета")}>
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
