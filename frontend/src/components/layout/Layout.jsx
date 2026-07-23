import { Link, NavLink } from 'react-router-dom';
import { useState } from 'react';
import PlatformStartLink from '../PlatformStartLink';
import TelegramContactLink from '../TelegramContactLink';
import { TELEGRAM_CHANNEL_URL } from '../../domain/siteConfig';
import './Layout.css';

const navLinks = [
  { to: '/', label: 'Главная' },
  { to: '/kak-nachat/', label: 'Как начать' },
  { to: '/oborudovanie/', label: 'Оборудование' },
  { to: '/articles/', label: 'Статьи' },
];

function Layout({ children }) {
  const [menuOpen, setMenuOpen] = useState(false);

  const closeMenu = () => setMenuOpen(false);

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand">
          <Link to="/" className="brand-link" onClick={closeMenu}>
            GrowerHub
          </Link>
          <span className="brand-tagline">Управление фермой в одном кабинете</span>
        </div>
        <button
          className="menu-toggle"
          type="button"
          onClick={() => setMenuOpen((prev) => !prev)}
          aria-label="Переключить меню"
        >
          ≡
        </button>
        <nav className={`nav-links ${menuOpen ? 'nav-open' : ''}`}>
          {navLinks.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => (isActive ? 'nav-link is-active' : 'nav-link')}
              onClick={closeMenu}
            >
              {item.label}
            </NavLink>
          ))}

          <NavLink
            to="/app/"
            className={({ isActive }) => (isActive ? 'nav-link app-link is-active' : 'nav-link app-link')}
            onClick={closeMenu}
          >
            Вход
          </NavLink>
          <PlatformStartLink placement="header" className="nav-link contact-link" onClick={closeMenu} />
          <TelegramContactLink
            placement="header_help"
            className="nav-link"
            onClick={closeMenu}
          >
            Помощь
          </TelegramContactLink>
        </nav>
      </header>
      <main className="app-main">{children}</main>
      <footer className="app-footer">
        <p>© {new Date().getFullYear()} GrowerHub. Все права защищены.</p>
        <div className="footer-links">
          <Link to="/about/">О проекте</Link>
          <Link to="/privacy/">Конфиденциальность</Link>
          <Link to="/terms/">Условия</Link>
          <a href={TELEGRAM_CHANNEL_URL} target="_blank" rel="noreferrer">Telegram-канал</a>
        </div>
      </footer>
    </div>
  );
}

export default Layout;
