import { useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import { getArticleClusterBySlug } from '../content/articleClusters';
import { getArticleBySlug, getRelatedArticles } from '../content/articles';
import useSeoMeta from '../utils/useSeoMeta';
import NotFoundPage from './NotFoundPage';

function ArticlePage() {
  const { slug } = useParams();
  const article = useMemo(() => getArticleBySlug(slug), [slug]);
  const cluster = useMemo(() => getArticleClusterBySlug(article?.cluster), [article]);
  const relatedArticles = useMemo(() => getRelatedArticles(article, 4), [article]);
  const heroImageRenderedInBody = Boolean(
    article?.hero_image && article.body.includes(`](${article.hero_image})`),
  );

  useSeoMeta({
    title: article ? `${article.title} - GrowerHub` : 'Статья не найдена - GrowerHub',
    description: article?.summary || 'Запрошенная статья GrowerHub не найдена.',
    path: article ? `/articles/${article.slug}/` : null,
    type: article ? 'article' : 'website',
    image: article?.hero_image || undefined,
    robots: article ? 'index,follow' : 'noindex,nofollow',
  });

  if (!article) {
    return <NotFoundPage />;
  }

  return (
    <article className="section">
      <div className="article-meta">
        Обновлено {new Date(article.updated_at).toLocaleDateString('ru-RU')}
      </div>
      <h1>{article.title}</h1>
      <p className="article-lead">{article.summary}</p>
      {cluster && (
        <Link to={`/articles/clusters/${cluster.slug}/`} className="secondary-link">
          {cluster.title}
        </Link>
      )}
      {article.hero_image && !heroImageRenderedInBody && (
        <img className="article-hero-image" src={article.hero_image} alt={article.hero_alt || article.title} />
      )}
      <div
        className="article-body"
        // Markdown prevrashaem v HTML dlya vyvoda.
        dangerouslySetInnerHTML={{ __html: article.bodyHtml }}
      />
      <LeadCta
        placement="article_bottom"
        title="Подключите устройство к GrowerHub"
        text="Войдите, настройте Zigbee2MQTT и увидьте метрики в кабинете. Если потребуется помощь, Telegram доступен на каждом шаге."
      />
      {relatedArticles.length > 0 && (
        <section className="related-articles">
          <h2>Читайте также</h2>
          <div className="articles-list">
            {relatedArticles.map((relatedArticle) => (
              <article className="article-card" key={relatedArticle.slug}>
                <Link to={`/articles/${relatedArticle.slug}/`}>{relatedArticle.title}</Link>
                <p>{relatedArticle.summary}</p>
              </article>
            ))}
          </div>
        </section>
      )}
      <div className="content-section">
        <Link to="/articles/" className="secondary-link">Назад к статьям</Link>
      </div>
    </article>
  );
}

export default ArticlePage;
