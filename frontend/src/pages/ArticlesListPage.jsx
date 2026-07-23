import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import { articleClusters } from '../content/articleClusters';
import { getArticlesByCluster } from '../content/articles';
import {
  getArticlePath,
  getClusterPath,
  getPublicPath,
} from '../domain/localizedRoutes';
import { getCurrentLocale, getIntlLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function ArticlesListPage() {
  const locale = getCurrentLocale();
  const description = translatePublic('articles.description');
  const path = getPublicPath('articles', locale);

  useSeoMeta({
    title: translatePublic('articles.title'),
    description,
    path,
    locale,
  });

  return (
    <div className="section">
      <h1>{translatePublic('Статьи GrowerHub')}</h1>
      <p>{description}</p>
      <div className="cluster-list">
        {articleClusters.map((cluster) => {
          const clusterArticles = getArticlesByCluster(cluster.id, locale);

          return (
            <section className="cluster-block" key={cluster.slug}>
              <div className="cluster-block__header">
                <div>
                  <h2>{cluster.title}</h2>
                  <p>{cluster.description}</p>
                </div>
                <Link to={getClusterPath(cluster, locale)} className="secondary-link">
                  {translatePublic('Раздел')}
                </Link>
              </div>
              <div className="cluster-meta-grid">
                <div>
                  <strong>{translatePublic('Подходит, если')}</strong>
                  <p>{cluster.fit}</p>
                </div>
                <div>
                  <strong>{translatePublic('Задачи, которые разбираем')}</strong>
                  <p>{cluster.tasks}</p>
                </div>
              </div>
              <div className="articles-list">
                {clusterArticles.map((article) => (
                  <article className="article-card" key={article.slug}>
                    <div className="article-meta">
                      {translatePublic('Обновлено')} {new Date(article.updated_at).toLocaleDateString(getIntlLocale(locale))}
                    </div>
                    <Link to={getArticlePath(article, locale)}>{article.title}</Link>
                    <p>{article.summary}</p>
                  </article>
                ))}
              </div>
            </section>
          );
        })}
      </div>
      <LeadCta placement="articles_index_bottom" />
    </div>
  );
}

export default ArticlesListPage;
