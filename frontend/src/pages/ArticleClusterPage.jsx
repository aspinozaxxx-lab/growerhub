import { Link, useParams } from 'react-router-dom';
import { articleClusters, getArticleClusterBySlug } from '../content/articleClusters';
import { getArticlesByCluster } from '../content/articles';

function ArticleClusterPage() {
  const { clusterSlug } = useParams();
  const cluster = getArticleClusterBySlug(clusterSlug);

  if (!cluster) {
    return (
      <div className="section">
        <h1>Раздел не найден</h1>
        <p>Проверьте ссылку или вернитесь к списку статей.</p>
        <Link to="/articles" className="hero-cta">
          К списку статей
        </Link>
      </div>
    );
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
          <strong>Портрет пользователя</strong>
          <p>{cluster.persona}</p>
        </div>
        <div>
          <strong>Коммерческий смысл</strong>
          <p>{cluster.commercialIntent}</p>
        </div>
      </div>

      <div className="keyword-list">
        {cluster.keywords.map((keyword) => (
          <span key={keyword}>{keyword}</span>
        ))}
      </div>

      <div className="cluster-block">
        <h2>Статьи раздела</h2>
        <div className="articles-list">
          {clusterArticles.map((article) => (
            <div className="article-card" key={article.slug}>
              <div className="article-meta">
                {new Date(article.created_at).toLocaleDateString('ru-RU')}
              </div>
              <Link to={`/articles/${article.slug}`}>{article.title}</Link>
              <p>{article.summary}</p>
            </div>
          ))}
        </div>
      </div>

      <div className="cluster-block">
        <h2>Другие разделы</h2>
        <div className="cluster-nav-grid">
          {otherClusters.map((item) => (
            <Link to={`/articles/clusters/${item.slug}`} key={item.slug}>
              {item.title}
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}

export default ArticleClusterPage;
