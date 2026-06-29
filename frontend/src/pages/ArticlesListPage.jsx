import { Link } from 'react-router-dom';
import { articleClusters } from '../content/articleClusters';
import { getArticlesByCluster } from '../content/articles';

function ArticlesListPage() {
  return (
    <div className="section">
      <h1>Статьи</h1>
      <p>Практические разделы GrowerHub сгруппированы по задачам: от безопасного автополива до Zigbee, журнала ухода, мини-фермы и DIY-интеграций.</p>
      <div className="cluster-list">
        {articleClusters.map((cluster) => {
          const clusterArticles = getArticlesByCluster(cluster.slug);

          return (
            <section className="cluster-block" key={cluster.slug}>
              <div className="cluster-block__header">
                <div>
                  <h2>{cluster.title}</h2>
                  <p>{cluster.description}</p>
                </div>
                <Link to={`/articles/clusters/${cluster.slug}`} className="secondary-link">
                  Раздел
                </Link>
              </div>
              <div className="cluster-meta-grid">
                <div>
                  <strong>Кому полезно</strong>
                  <p>{cluster.persona}</p>
                </div>
                <div>
                  <strong>Коммерческий смысл</strong>
                  <p>{cluster.commercialIntent}</p>
                </div>
              </div>
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
            </section>
          );
        })}
      </div>
    </div>
  );
}

export default ArticlesListPage;
