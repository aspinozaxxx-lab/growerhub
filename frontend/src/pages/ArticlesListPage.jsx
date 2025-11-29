import { Link } from 'react-router-dom';
import { articles } from '../content/articles';

function ArticlesListPage() {
  return (
    <div className="section">
      <h1>Статьи</h1>
      <p>Подборка быстрых гайдов по настройке GrowerHub и практических сценариев автоматизации.</p>
      <div className="articles-list" style={{ marginTop: 14 }}>
        {articles.map((article) => (
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
  );
}

export default ArticlesListPage;
