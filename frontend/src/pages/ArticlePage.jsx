import { useEffect, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getArticleClusterBySlug } from '../content/articleClusters';
import { getArticleBySlug, getRelatedArticles } from '../content/articles';

const SITE_URL = 'https://growerhub.ru';

const setMetaDescription = (content) => {
  let meta = document.querySelector('meta[name="description"]');
  if (!meta) {
    meta = document.createElement('meta');
    meta.setAttribute('name', 'description');
    document.head.appendChild(meta);
  }
  meta.setAttribute('content', content);
};

const setJsonLd = (article) => {
  const scriptId = 'article-json-ld';
  let script = document.getElementById(scriptId);
  if (!script) {
    script = document.createElement('script');
    script.id = scriptId;
    script.type = 'application/ld+json';
    document.head.appendChild(script);
  }

  script.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    headline: article.title,
    description: article.summary,
    datePublished: article.created_at,
    dateModified: article.updated_at || article.created_at,
    image: article.hero_image ? `${SITE_URL}${article.hero_image}` : undefined,
    mainEntityOfPage: `${SITE_URL}/articles/${article.slug}`,
    publisher: {
      '@type': 'Organization',
      name: 'GrowerHub',
      url: SITE_URL,
    },
  });
};

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
      setMetaDescription('Статья GrowerHub не найдена.');
      document.getElementById('article-json-ld')?.remove();
      return;
    }

    document.title = `${article.title} - GrowerHub`;
    setMetaDescription(article.summary || 'Практическая статья GrowerHub об уходе за растениями.');
    setJsonLd(article);

    return () => {
      document.getElementById('article-json-ld')?.remove();
    };
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
