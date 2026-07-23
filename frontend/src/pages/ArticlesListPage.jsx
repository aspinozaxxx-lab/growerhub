import { Link } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import { articleClusters } from '../content/articleClusters';
import { getArticlesByCluster } from '../content/articles';
import useSeoMeta from '../utils/useSeoMeta';

const description = 'Практические материалы GrowerHub об автополиве, датчиках, Zigbee2MQTT, Home Assistant и автоматизации мини-фермы.';

function ArticlesListPage() {
  useSeoMeta({
    title: 'Статьи GrowerHub — датчики, полив и автоматизация',
    description,
    path: '/articles/',
  });

  return (
    <div className="section">
      <h1>Статьи GrowerHub</h1>
      <p>{description}</p>
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
                <Link to={`/articles/clusters/${cluster.slug}/`} className="secondary-link">
                  Раздел
                </Link>
              </div>
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
          );
        })}
      </div>
      <LeadCta placement="articles_index_bottom" />
    </div>
  );
}

export default ArticlesListPage;
