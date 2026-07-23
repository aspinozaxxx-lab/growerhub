import { Link } from 'react-router-dom';
import { getPublicPath } from '../domain/localizedRoutes';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function NotFoundPage() {
  const locale = getCurrentLocale();
  const description = translatePublic('404.description');
  useSeoMeta({
    title: translatePublic('404.title'),
    description,
    path: null,
    robots: 'noindex,nofollow',
    locale,
  });

  return (
    <div className="section not-found-page">
      <div className="article-meta">{translatePublic('Ошибка 404')}</div>
      <h1>{translatePublic('Страница не найдена')}</h1>
      <p>{description}</p>
      <div className="cta-row">
        <Link to={getPublicPath('home', locale)} className="hero-cta">{translatePublic('На главную')}</Link>
        <Link to={getPublicPath('articles', locale)} className="secondary-link">{translatePublic('К статьям')}</Link>
      </div>
    </div>
  );
}

export default NotFoundPage;
