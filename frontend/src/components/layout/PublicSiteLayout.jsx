import { useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { Leaf, Menu, ShoppingBag, X } from 'lucide-react';
import { useShop } from '../../features/shop';
import { getPublicLocale, getPublicPath } from '../../domain/localizedRoutes';
import { getLocalizedPathPair } from '../../content/localizedNavigation';
import { GITHUB_REPOSITORY_URL, TELEGRAM_CHANNEL_URL } from '../../domain/siteConfig';
import './PublicSiteLayout.css';

export default function PublicSiteLayout({ children }) {
  const { pathname } = useLocation();
  const locale = getPublicLocale(pathname);
  const en = locale === 'en';
  const [menuOpen, setMenuOpen] = useState(false);
  const { cartCount, openConsultation } = useShop();
  const pair = getLocalizedPathPair(pathname);
  const closeMenu = () => setMenuOpen(false);
  const consult = () => { closeMenu(); openConsultation(); };
  const footerLinks = [
    ['articles', en ? 'Plant guides' : 'Статьи о растениях'],
    ['news', en ? 'What’s new' : 'Новости'],
    ['about', en ? 'About GrowerHub' : 'О GrowerHub'],
    ['equipmentCoordinators', en ? 'Your own equipment' : 'Своё оборудование'],
    ['privacy', en ? 'Privacy' : 'Конфиденциальность'],
    ['terms', en ? 'Terms & orders' : 'Условия и заказы'],
  ];
  return (
    <div className="app-shell app-shell--public">
      <a className="public-skip" href="#public-main">{en ? 'Skip to content' : 'Перейти к содержимому'}</a>
      <header className="public-header">
        <Link to={getPublicPath('home', locale)} className="public-brand" onClick={closeMenu} aria-label={en ? 'GrowerHub home' : 'GrowerHub — главная'}>
          <Leaf size={27} aria-hidden="true" /><span>GrowerHub</span>
        </Link>
        <nav id="public-navigation" className={`public-nav${menuOpen ? ' public-nav--open' : ''}`} aria-label={en ? 'Main navigation' : 'Главное меню'}>
          <NavLink to={getPublicPath('equipment', locale)} onClick={closeMenu}>{en ? 'Ready-made kits' : 'Комплекты'}</NavLink>
          <NavLink to={getPublicPath('gettingStarted', locale)} onClick={closeMenu}>{en ? 'Getting started' : 'Как начать'}</NavLink>
          <button type="button" onClick={consult}>{en ? 'Help choosing' : 'Помощь'}</button>
          <Link to={`/app/?lang=${locale}`} className="public-login" onClick={closeMenu}>{en ? 'Sign in' : 'Войти'}</Link>
          <a href={en ? pair.ru : pair.en} className="locale-switch" hrefLang={en ? 'ru' : 'en'}>{en ? 'RU' : 'EN'}</a>
        </nav>
        <Link to={getPublicPath('cart', locale)} className="public-cart" onClick={closeMenu} aria-label={`${en ? 'Cart' : 'Корзина'}${cartCount ? `: ${cartCount}` : ''}`}>
          <ShoppingBag size={24} aria-hidden="true" /><span className="public-cart__label">{en ? 'Cart' : 'Корзина'}</span>
          {cartCount > 0 && <span className="public-cart__count">{cartCount}</span>}
        </Link>
        <button className="public-menu-toggle" type="button" aria-label={en ? (menuOpen ? 'Close menu' : 'Open menu') : (menuOpen ? 'Закрыть меню' : 'Открыть меню')} aria-expanded={menuOpen} aria-controls="public-navigation" onClick={() => setMenuOpen(!menuOpen)}>
          {menuOpen ? <X size={24} /> : <Menu size={24} />}
        </button>
      </header>
      <main id="public-main" className="app-main public-main">{children}</main>
      <footer className="public-footer">
        <div className="public-footer__intro"><strong>GrowerHub <span aria-hidden="true">♡</span></strong><p>{en ? 'More time for the plants you love.' : 'Больше времени на любимые растения.'}</p></div>
        <nav className="public-footer__links" aria-label={en ? 'More about the project' : 'Ещё о проекте'}>
          {footerLinks.map(([id, label]) => <Link key={id} to={getPublicPath(id, locale)}>{label}</Link>)}
          <a href={TELEGRAM_CHANNEL_URL} target="_blank" rel="noreferrer">{en ? 'Telegram channel' : 'Канал в Telegram'}</a>
          <a href={GITHUB_REPOSITORY_URL} target="_blank" rel="noreferrer">GitHub</a>
        </nav>
        <p className="public-footer__copyright">© {new Date().getFullYear()} GrowerHub</p>
      </footer>
    </div>
  );
}
