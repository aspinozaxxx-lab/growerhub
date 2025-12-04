import { Link, NavLink } from 'react-router-dom';
import { useState } from 'react';
import './Layout.css';

const navLinks = [
  { to: '/', label: 'Р“Р»Р°РІРЅР°СЏ' },
  { to: '/articles', label: 'РЎС‚Р°С‚СЊРё' },
  { to: '/about', label: 'Рћ РїСЂРѕРµРєС‚Рµ' },
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
          <span className="brand-tagline">СѓРјРЅС‹Р№ РєРѕРЅС‚СЂРѕР»СЊ РїРѕР»РёРІР°</span>
        </div>
        <button
          className="menu-toggle"
          type="button"
          onClick={() => setMenuOpen((prev) => !prev)}
          aria-label="РџРµСЂРµРєР»СЋС‡РёС‚СЊ РјРµРЅСЋ"
        >
          в°
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
            className={({ isActive }) => (isActive ? 'nav-link is-active' : 'nav-link')}
            onClick={closeMenu}
          >
            Новое приложение
          </NavLink>
          <a
            className="nav-link app-link"
            href="/static/index.html"
            onClick={closeMenu}
          >
            Старое приложение (legacy)
          </a>
        </nav>
      </header>
      <main className="app-main">{children}</main>
      <footer className="app-footer">
        <p>В© {new Date().getFullYear()} GrowerHub. РђРІС‚РѕРјР°С‚РёР·Р°С†РёСЏ РїРѕР»РёРІР° Рё РјРѕРЅРёС‚РѕСЂРёРЅРі С‚РµРїР»РёС†.</p>
      </footer>
    </div>
  );
}

export default Layout;

