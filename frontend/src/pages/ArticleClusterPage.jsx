import { Link, useParams } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import {
  getArticleClusterById,
  getArticleClusterBySlug,
  getArticleClusters,
} from '../content/articleClusters';
import { getArticleById, getArticlesByCluster } from '../content/articles';
import {
  getArticlePath,
  getClusterPath,
} from '../domain/localizedRoutes';
import { getCurrentLocale, getIntlLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';
import NotFoundPage from './NotFoundPage';

function ArticleClusterPage() {
  const { clusterSlug } = useParams();
  const locale = getCurrentLocale();
  const cluster = getArticleClusterBySlug(clusterSlug, locale);
  const ruCluster = cluster ? getArticleClusterById(cluster.id, 'ru') : null;
  const enCluster = cluster ? getArticleClusterById(cluster.id, 'en') : null;
  const path = cluster ? getClusterPath(cluster, locale) : null;

  useSeoMeta({
    title: cluster ? `${cluster.title} — GrowerHub` : translatePublic('Раздел не найден — GrowerHub'),
    description: cluster?.description || translatePublic('Запрошенный раздел GrowerHub не найден.'),
    path,
    robots: cluster ? 'index,follow' : 'noindex,nofollow',
    locale,
    alternatePaths: ruCluster && enCluster ? {
      ru: getClusterPath(ruCluster, 'ru'),
      en: getClusterPath(enCluster, 'en'),
    } : undefined,
  });

  if (!cluster) {
    return <NotFoundPage />;
  }

  const clusterArticles = getArticlesByCluster(cluster.id, locale);
  const otherClusters = getArticleClusters(locale).filter((item) => item.slug !== cluster.slug);
  const guideSteps = cluster.guide.steps.map((step) => ({
    ...step,
    article: getArticleById(step.articleId, locale),
  }));

  return (
    <div className="section">
      <div className="article-meta">{translatePublic('Практический раздел')}</div>
      <h1>{cluster.title}</h1>
      <p>{cluster.description}</p>

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

      <section className="cluster-block cluster-guide">
        <h2>{translatePublic('С чего начать')}</h2>
        <p>{cluster.guide.intro}</p>
        <div className="cluster-path-grid">
          {guideSteps.map((step) => (
            <article className="article-card" key={step.articleId}>
              {step.article ? (
                <Link to={getArticlePath(step.article, locale)}>{step.title}</Link>
              ) : (
                <strong>{step.title}</strong>
              )}
              <p>{step.text}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="cluster-block">
        <h2>{translatePublic('Выберите первый шаг')}</h2>
        <div className="cluster-table-wrap">
          <table className="cluster-decision-table">
            <thead>
              <tr>
                <th>{translatePublic('Ситуация')}</th>
                <th>{translatePublic('С чего начать')}</th>
                <th>{translatePublic('Почему')}</th>
              </tr>
            </thead>
            <tbody>
              {cluster.guide.decisions.map((decision) => (
                <tr key={decision.situation}>
                  <td>{decision.situation}</td>
                  <td>{decision.start}</td>
                  <td>{decision.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="cluster-block">
        <h2>{translatePublic('Статьи раздела')}</h2>
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

      <LeadCta
        placement="cluster_bottom"
        title={cluster.guide.cta.title}
        text={cluster.guide.cta.text}
      />

      <section className="cluster-block">
        <h2>{translatePublic('Другие разделы')}</h2>
        <div className="cluster-nav-grid">
          {otherClusters.map((item) => (
            <Link to={getClusterPath(item, locale)} key={item.slug}>
              {item.title}
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}

export default ArticleClusterPage;
