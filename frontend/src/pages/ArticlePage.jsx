import { useEffect, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getArticleClusterBySlug } from '../content/articleClusters';
import { getArticleBySlug, getRelatedArticles } from '../content/articles';

function ArticlePage() {
  const { slug } = useParams();
  const article = useMemo(() => getArticleBySlug(slug), [slug]);
  const cluster = useMemo(() => getArticleClusterBySlug(article?.cluster), [article]);
  const relatedArticles = useMemo(() => getRelatedArticles(article, 3), [article]);
  const heroImageRenderedInBody = Boolean(
    article?.hero_image && article.body.includes(`](${article.hero_image})`),
  );

  useEffect(() => {
    if (!article) {
      document.title = 'Статья не найдена - GrowerHub';
      return;
    }

    document.title = `${article.title} - GrowerHub`;
  }, [article]);

  if (!article) {
    return (
      <div className="section">
        <h1>Статья не найдена</h1>
        <p>Проверьте ссылку или вернитесь к списку.</p>
        <Link to="/articles" className="hero-cta">
          К списку статей
        </Link>
      </div>
    );
  }

  return (
    <div className="section">
      <div className="article-meta">
        {new Date(article.created_at).toLocaleDateString('ru-RU')}
      </div>
      <h1>{article.title}</h1>
      <p>{article.summary}</p>
      {cluster && (
        <Link to={`/articles/clusters/${cluster.slug}`} className="secondary-link">
          {cluster.title}
        </Link>
      )}
      {article.hero_image && !heroImageRenderedInBody && (
        <img className="article-hero-image" src={article.hero_image} alt={article.hero_alt || article.title} />
      )}
      <div
        className="article-body"
        // Markdown prevrashaem v HTML dlya vyvoda
        dangerouslySetInnerHTML={{ __html: article.bodyHtml }}
      />
      {relatedArticles.length > 0 && (
        <div className="related-articles">
          <h2>Читайте также</h2>
          <div className="articles-list">
            {relatedArticles.map((relatedArticle) => (
              <div className="article-card" key={relatedArticle.slug}>
                <Link to={`/articles/${relatedArticle.slug}`}>{relatedArticle.title}</Link>
                <p>{relatedArticle.summary}</p>
              </div>
            ))}
          </div>
        </div>
      )}
      <div style={{ marginTop: 16 }}>
        <Link to="/articles" className="hero-cta" style={{ padding: '10px 14px' }}>
          Назад к статьям
        </Link>
      </div>
    </div>
  );
}

export default ArticlePage;
