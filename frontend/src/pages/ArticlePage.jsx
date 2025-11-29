import { useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getArticleBySlug } from '../content/articles';

function ArticlePage() {
  const { slug } = useParams();
  const article = useMemo(() => getArticleBySlug(slug), [slug]);

  if (!article) {
    return (
      <div className="section">
        <h1>?????? ?? ???????</h1>
        <p>????????? ?????? ??? ????????? ? ??????.</p>
        <Link to="/articles" className="hero-cta">
          ? ?????? ??????
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
      <div
        className="article-body"
        // Markdown prevrashaem v HTML dlya vyvoda
        dangerouslySetInnerHTML={{ __html: article.bodyHtml }}
      />
      <div style={{ marginTop: 16 }}>
        <Link to="/articles" className="hero-cta" style={{ padding: '10px 14px' }}>
          ????? ? ???????
        </Link>
      </div>
    </div>
  );
}

export default ArticlePage;
