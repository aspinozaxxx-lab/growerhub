import { Link, useParams } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import { articleClusters, getArticleClusterBySlug } from '../content/articleClusters';
import { getArticlesByCluster } from '../content/articles';
import useSeoMeta from '../utils/useSeoMeta';
import NotFoundPage from './NotFoundPage';

function ArticleClusterPage() {
  const { clusterSlug } = useParams();
  const cluster = getArticleClusterBySlug(clusterSlug);

  useSeoMeta({
    title: cluster ? `${cluster.title} - статьи GrowerHub` : 'Раздел не найден - GrowerHub',
    description: cluster?.description || 'Запрошенный раздел GrowerHub не найден.',
    path: cluster ? `/articles/clusters/${cluster.slug}/` : null,
    robots: cluster ? 'index,follow' : 'noindex,nofollow',
  });

  if (!cluster) {
    return <NotFoundPage />;
  }

  const clusterArticles = getArticlesByCluster(cluster.slug);
  const otherClusters = articleClusters.filter((item) => item.slug !== cluster.slug);

  return (
    <div className="section">
      <div className="article-meta">Практический раздел</div>
      <h1>{cluster.title}</h1>
      <p>{cluster.description}</p>

      <div className="cluster-meta-grid">
        <div>
          <strong>Подходит, если</strong>
          <p>{cluster.fit}</p>
        </div>
        <div>
          <strong>Задачи, которые разбираем</strong>
          <p>{cluster.tasks}</p>
        </div>
      </div>

      <div className="keyword-list">
        {cluster.keywords.map((keyword) => (
          <span key={keyword}>{keyword}</span>
        ))}
      </div>

      <section className="cluster-block">
        <h2>Статьи раздела</h2>
        <div className="articles-list">
          {clusterArticles.map((article) => (
            <article className="article-card" key={article.slug}>
              <div className="article-meta">
                Обновлено {new Date(article.updated_at).toLocaleDateString('ru-RU')}
              </div>
              <Link to={`/articles/${article.slug}/`}>{article.title}</Link>
              <p>{article.summary}</p>
            </article>
          ))}
        </div>
      </section>

      <LeadCta placement="cluster_bottom" />

      <section className="cluster-block">
        <h2>Другие разделы</h2>
        <div className="cluster-nav-grid">
          {otherClusters.map((item) => (
            <Link to={`/articles/clusters/${item.slug}/`} key={item.slug}>
              {item.title}
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}

export default ArticleClusterPage;
