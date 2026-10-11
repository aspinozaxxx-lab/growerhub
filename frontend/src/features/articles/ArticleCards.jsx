import { Bookmark, ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { getArticleClusterById } from '../../content/articleClusters';
import { getArticlePath, getClusterPath, getPublicPath } from '../../domain/localizedRoutes';
import { getIntlLocale } from '../../locales/i18n';
import useArticleLibrary, { toggleSavedArticle } from './useArticleLibrary';
import './articles.css';

export function ArticleSaveButton({ article, locale, compact = false }) {
  const library = useArticleLibrary();
  const saved = library.saved.includes(article.id);
  const label = locale === 'en' ? (saved ? 'Saved' : 'Save article') : (saved ? 'Сохранено' : 'Сохранить статью');
  return (
    <button type="button" className={`article-save${saved ? ' article-save--active' : ''}`} aria-pressed={saved}
      aria-label={`${label}: ${article.title}`} title={label} onClick={() => toggleSavedArticle(article.id)}>
      <Bookmark size={18} fill={saved ? 'currentColor' : 'none'} aria-hidden="true" />
      {!compact && <span>{label}</span>}
    </button>
  );
}

export function ArticleStorageNotice({ locale }) {
  const library = useArticleLibrary();
  if (library.persistent) return null;
  return <p role="status" className="article-storage-notice">{locale === 'en'
    ? 'Browser storage is unavailable. Your saved and recent articles will stay here until you close or reload this page.'
    : 'Браузер не разрешает постоянное хранение. Закладки и история останутся до закрытия или перезагрузки страницы.'}</p>;
}

export default function ArticleCards({ articles, locale }) {
  return <div className="article-library-grid">{articles.map((article) => {
    const cluster = getArticleClusterById(article.cluster, locale);
    return (
      <article className="article-library-card" key={article.id}>
        {article.hero_image && <Link to={getArticlePath(article, locale)} tabIndex={-1} aria-hidden="true" className="article-library-card__image">
          <img src={article.hero_image} alt="" loading="lazy" decoding="async" width="640" height="360" />
        </Link>}
        <div className="article-library-card__body">
          <div className="article-library-card__meta">
            {cluster && <Link to={getClusterPath(cluster, locale)}>{cluster.label}</Link>}
            <ArticleSaveButton article={article} locale={locale} compact />
          </div>
          <h3><Link to={getArticlePath(article, locale)}>{article.title}</Link></h3>
          <p>{article.summary}</p>
          <time dateTime={article.updated_at}>{locale === 'en' ? 'Updated ' : 'Обновлено '}{new Date(article.updated_at).toLocaleDateString(getIntlLocale(locale), { timeZone: 'UTC' })}</time>
        </div>
      </article>
    );
  })}</div>;
}

export function ArticleBreadcrumbs({ cluster, locale }) {
  return <nav className="article-breadcrumbs" aria-label={locale === 'en' ? 'Breadcrumbs' : 'Путь к статье'}>
    <Link to={getPublicPath('articles', locale)}>{locale === 'en' ? 'Plant guides' : 'Статьи'}</Link>
    {cluster && <><ChevronRight size={15} aria-hidden="true" /><Link to={getClusterPath(cluster, locale)}>{cluster.label}</Link></>}
  </nav>;
}
