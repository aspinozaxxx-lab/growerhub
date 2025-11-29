import { Link, NavLink } from 'react-router-dom';
import { useState } from 'react';
import './Layout.css';

const navLinks = [
  { to: '/', label: '???????' },
  { to: '/articles', label: '??????' },
  { to: '/about', label: '? ???????' },
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
          <span className="brand-tagline">????? ???????? ??????</span>
        </div>
        <button
          className="menu-toggle"
          type="button"
          onClick={() => setMenuOpen((prev) => !prev)}
          aria-label="??????????? ????"
        >
          ?
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
          <a
            className="nav-link app-link"
            href="/static/index.html"
            onClick={closeMenu}
          >
            ??????? ??????????
          </a>
        </nav>
      </header>
      <main className="app-main">{children}</main>
      <footer className="app-footer">
        <p>? {new Date().getFullYear()} GrowerHub. ????????????? ?????? ? ?????????? ??????.</p>
      </footer>
    </div>
  );
}

export default Layout;
