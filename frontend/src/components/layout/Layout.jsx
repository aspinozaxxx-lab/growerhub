import { Link, NavLink } from 'react-router-dom';
import { useState } from 'react';
import './Layout.css';

const navLinks = [
  { to: '/', label: 'Главная' },
  { to: '/articles', label: 'Статьи' },
  { to: '/about', label: 'О проекте' },
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
          <span className="brand-tagline">Умный контроль полива</span>
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
              className={({ isActive }) => (isActive ? 'nav-link is-active' : 'nav-link')}
              onClick={closeMenu}
            >
              {item.label}
            </NavLink>
          ))}

          <NavLink
            to="/app"
            className={({ isActive }) => (isActive ? 'nav-link app-link is-active' : 'nav-link app-link')}
            onClick={closeMenu}
          >
            Новое приложение
          </NavLink>
        </nav>
      </header>
      <main className="app-main">{children}</main>
      <footer className="app-footer">
        <p>© {new Date().getFullYear()} GrowerHub. Все права защищены.</p>
      </footer>
    </div>
  );
}

export default Layout;
