import { Link } from 'react-router-dom';
import { articles } from '../content/articles';
import { homeContent } from '../content/pages';

function HomePage() {
  const recentArticles = articles.slice(0, 2);

  return (
    <div className="section">
      <div className="hero">
        <div>
          <div className="badge">GrowerHub · умный полив</div>
          <h1>{homeContent.hero.title}</h1>
          <p>{homeContent.hero.subtitle}</p>
          <a className="hero-cta" href="/static/index.html">
            {homeContent.hero.cta}
          </a>
        </div>
        <div className="card">
          <h3>{homeContent.secondary.title}</h3>
          <p>{homeContent.secondary.text}</p>
          <div className="card-grid">
            {homeContent.secondary.points.map((point) => (
              <div key={point.title} className="info-block">
                <strong>{point.title}</strong>
                <p>{point.text}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="section" style={{ marginTop: 24 }}>
        <h2>{homeContent.features.title}</h2>
        <div className="card-grid">
          {homeContent.features.items.map((item) => (
            <div className="card" key={item.title}>
              <h3>{item.title}</h3>
              <p>{item.text}</p>
            </div>
          ))}
        </div>
      </div>

      <div className="section" style={{ marginTop: 24 }}>
        <h2>Свежие статьи</h2>
        <div className="articles-list">
          {recentArticles.map((article) => (
            <div className="article-card" key={article.slug}>
              <div className="article-meta">
                {new Date(article.created_at).toLocaleDateString('ru-RU')}
              </div>
              <Link to={/articles/}>{article.title}</Link>
              <p>{article.summary}</p>
            </div>
          ))}
        </div>
        <div style={{ marginTop: 14 }}>
          <Link to="/articles" className="hero-cta" style={{ padding: '10px 14px' }}>
            Все статьи
          </Link>
        </div>
      </div>
    </div>
  );
}

export default HomePage;
