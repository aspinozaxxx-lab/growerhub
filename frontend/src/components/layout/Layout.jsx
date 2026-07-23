import { Link, NavLink, useLocation } from 'react-router-dom';
import { useState } from 'react';
import PlatformStartLink from '../PlatformStartLink';
import TelegramContactLink from '../TelegramContactLink';
import { TELEGRAM_CHANNEL_URL } from '../../domain/siteConfig';
import { getPublicLocale, getPublicPath, isAppPath } from '../../domain/localizedRoutes';
import { getLocalizedPathPair } from '../../content/localizedNavigation';
import { getCurrentLocale, translatePublic } from '../../locales/i18n';
import './Layout.css';

const navLinks = [
  { routeId: 'home', label: 'Главная' },
  { routeId: 'gettingStarted', label: 'Как начать' },
  { routeId: 'equipment', label: 'Оборудование' },
  { routeId: 'articles', label: 'Статьи' },
];

function Layout({ children }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const inApp = isAppPath(location.pathname);
  const publicLocale = inApp ? getCurrentLocale() : getPublicLocale(location.pathname);
  const pathPair = getLocalizedPathPair(location.pathname);

  const closeMenu = () => setMenuOpen(false);

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand">
          <Link to={getPublicPath('home', publicLocale)} className="brand-link" onClick={closeMenu}>
            GrowerHub
          </Link>
          <span className="brand-tagline">{translatePublic('Управление фермой в одном кабинете')}</span>
        </div>
        <button
          className="menu-toggle"
          type="button"
          onClick={() => setMenuOpen((prev) => !prev)}
          aria-label={translatePublic('Переключить меню')}
        >
          ≡
        </button>
        <nav className={`nav-links ${menuOpen ? 'nav-open' : ''}`}>
          {navLinks.map((item) => {
            const to = getPublicPath(item.routeId, publicLocale);
            return (
            <NavLink
              key={item.routeId}
              to={to}
              end={item.routeId === 'home'}
              className={({ isActive }) => (isActive ? 'nav-link is-active' : 'nav-link')}
              onClick={closeMenu}
            >
              {translatePublic(item.label)}
            </NavLink>
            );
          })}

          <NavLink
            to="/app/"
            className={({ isActive }) => (isActive ? 'nav-link app-link is-active' : 'nav-link app-link')}
            onClick={closeMenu}
          >
            {translatePublic('Вход')}
          </NavLink>
          <PlatformStartLink placement="header" className="nav-link contact-link" onClick={closeMenu} />
          <TelegramContactLink
            placement="header_help"
            className="nav-link"
            onClick={closeMenu}
          >
            {translatePublic('Помощь')}
          </TelegramContactLink>
          {!inApp && (
            <a
              className="nav-link locale-switch"
              href={publicLocale === 'ru' ? pathPair.en : pathPair.ru}
              hrefLang={publicLocale === 'ru' ? 'en' : 'ru'}
              onClick={closeMenu}
            >
              {publicLocale === 'ru' ? 'EN' : 'RU'}
            </a>
          )}
        </nav>
      </header>
      <main className="app-main">{children}</main>
      <footer className="app-footer">
        <p>© {new Date().getFullYear()} GrowerHub. {translatePublic('Все права защищены.')}</p>
        <div className="footer-links">
          <Link to={getPublicPath('about', publicLocale)}>{translatePublic('О проекте')}</Link>
          <Link to={getPublicPath('privacy', publicLocale)}>{translatePublic('Конфиденциальность')}</Link>
          <Link to={getPublicPath('terms', publicLocale)}>{translatePublic('Условия')}</Link>
          <a href={TELEGRAM_CHANNEL_URL} target="_blank" rel="noreferrer">{translatePublic('Telegram-канал')}</a>
        </div>
      </footer>
    </div>
  );
}

export default Layout;
