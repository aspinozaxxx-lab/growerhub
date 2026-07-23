import { Link } from 'react-router-dom';
import useSeoMeta from '../utils/useSeoMeta';

const description = 'Страница GrowerHub не найдена. Вернитесь на главную страницу или к практическим материалам.';

function NotFoundPage() {
  useSeoMeta({
    title: 'Страница не найдена - GrowerHub',
    description,
    path: null,
    robots: 'noindex,nofollow',
  });

  return (
    <div className="section not-found-page">
      <div className="article-meta">Ошибка 404</div>
      <h1>Страница не найдена</h1>
      <p>{description}</p>
      <div className="cta-row">
        <Link to="/" className="hero-cta">На главную</Link>
        <Link to="/articles/" className="secondary-link">К статьям</Link>
      </div>
    </div>
  );
}

export default NotFoundPage;
