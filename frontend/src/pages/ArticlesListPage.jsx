import { createElement } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { BookOpen, Bookmark, Clock3, Droplets, Flower2, Leaf, Plug, Search, Sun, Wrench, X } from 'lucide-react';
import { getArticleClusters } from '../content/articleClusters';
import { getArticles, getArticlesByCluster } from '../content/articles';
import { getClusterPath, getPublicPath } from '../domain/localizedRoutes';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';
import ArticleCards, { ArticleStorageNotice } from '../features/articles/ArticleCards';
import useArticleLibrary, { clearRecentArticles } from '../features/articles/useArticleLibrary';

const icons = { 'vyrashchivanie-i-uhod': Leaf, 'svet-dlya-rasteniy': Sun,
  'avtopoliv-i-kontroller-vyrashchivaniya': Droplets, 'zhurnal-i-sovetnik-uhoda': BookOpen,
  'zigbee-hub-i-ustroystva': Plug, 'mini-ferma-i-neskolko-boksov': Flower2, 'home-assistant-i-diy': Wrench };
const normalize = (value) => value.toLocaleLowerCase().replaceAll('ё', 'е').normalize('NFKC');

function ArticlesListPage() {
  const locale = getCurrentLocale();
  const en = locale === 'en';
  const library = useArticleLibrary();
  const [params, setParams] = useSearchParams();
  // Staticheskij HTML i pervyj render dolzhny sovpadat' pri ssylke s poiskom.
  const query = library.ready ? (params.get('q') || '').slice(0, 200) : '';
  const view = library.ready && ['saved', 'recent'].includes(params.get('view')) ? params.get('view') : 'all';
  const clusters = getArticleClusters(locale);
  const all = getArticles(locale);
  const byId = new Map(all.map((article) => [article.id, article]));
  const source = view === 'all' ? all : (view === 'saved' ? library.saved : library.recent).map((id) => byId.get(id)).filter(Boolean);
  const terms = normalize(query).trim().split(/\s+/).filter(Boolean);
  const results = source.filter((article) => {
    const cluster = clusters.find((item) => item.id === article.cluster);
    const text = normalize(`${article.title} ${article.summary} ${cluster?.label || ''}`);
    return terms.every((term) => text.includes(term));
  });
  const overview = view === 'all' && !terms.length;
  const shown = overview ? results.slice(0, 6) : results;
  const setQuery = (value) => {
    const next = new URLSearchParams(params);
    if (value) next.set('q', value); else next.delete('q');
    setParams(next, { replace: true, preventScrollReset: true });
  };
  const viewLink = (target) => {
    const next = new URLSearchParams();
    if (query) next.set('q', query);
    if (target !== 'all') next.set('view', target);
    return `${getPublicPath('articles', locale)}${next.size ? `?${next}` : ''}`;
  };
  useSeoMeta({ title: translatePublic('articles.title'), description: translatePublic('articles.description'), path: getPublicPath('articles', locale), locale });

  return <div className="section article-library">
    <div className="article-library-intro">
      <p className="article-library-eyebrow">{en ? 'Your growing bookshelf' : 'На полку к любимым растениям'}</p>
      <h1>{en ? 'Useful reads for your plants' : 'Полезное о растениях'}</h1>
      <p>{en ? 'Care, light, watering and helpful equipment. Find an answer for today and save what you want to return to.' : 'Уход, свет, полив и полезная техника. Найдите ответ на сегодня, а то, что пригодится потом, сохраните.'}</p>
    </div>
    <div className="article-library-toolbar">
      <label className="article-library-search">
        <Search size={20} aria-hidden="true" />
        <input type="search" aria-label={en ? 'Search articles' : 'Поиск по статьям'} maxLength={200} value={query}
          onChange={(event) => setQuery(event.target.value)} placeholder={en ? 'Plant, question or a word you remember' : 'Растение, вопрос или слово из статьи'} />
        {query && <button className="article-save" type="button" aria-label={en ? 'Clear search' : 'Очистить поиск'} onClick={() => setQuery('')}><X size={18} aria-hidden="true" /></button>}
      </label>
      <nav className="article-library-tabs" aria-label={en ? 'Article lists' : 'Списки статей'}>
        {[
          ['all', BookOpen, en ? 'All articles' : 'Все статьи', null],
          ['saved', Bookmark, en ? 'Saved' : 'Сохранённые', library.saved.length],
          ['recent', Clock3, en ? 'Recently opened' : 'Недавно открывали', library.recent.length],
        ].map(([id, icon, label, count]) => <Link key={id} to={viewLink(id)} preventScrollReset aria-current={view === id ? 'page' : undefined}>
          {createElement(icon, { size: 18, 'aria-hidden': true })}{label}{count > 0 && <small>{count}</small>}
        </Link>)}
      </nav>
    </div>
    <ArticleStorageNotice locale={locale} />
    {overview && <section aria-labelledby="article-topics-heading">
      <h2 id="article-topics-heading">{en ? 'Choose a topic' : 'Что сейчас интересно?'}</h2>
      <div className="article-topic-grid">{clusters.map((cluster) => {
        const Icon = icons[cluster.id] || BookOpen;
        return <Link className="article-topic-card" to={getClusterPath(cluster, locale)} key={cluster.id}>
          <Icon size={24} aria-hidden="true" /><strong>{cluster.label}</strong>
          <span>{en ? 'Articles: ' : 'Материалов: '}{getArticlesByCluster(cluster.id, locale).length}</span>
        </Link>;
      })}</div>
    </section>}
    <section aria-labelledby="article-results-heading">
      <div className="article-library-heading">
        <h2 id="article-results-heading">{overview ? (en ? 'Fresh on the shelf' : 'Свежие материалы') : view === 'saved' ? (en ? 'Saved articles' : 'Сохранённые статьи') : view === 'recent' ? (en ? 'Recently opened' : 'Недавно открывали') : (en ? 'Search results' : 'Результаты поиска')}</h2>
        {terms.length > 0 && <p role="status">{en ? 'Found: ' : 'Найдено: '}{results.length}</p>}
        {view === 'recent' && library.recent.length > 0 && <button type="button" className="article-save" onClick={clearRecentArticles}>{en ? 'Clear history' : 'Очистить историю'}</button>}
      </div>
      {view !== 'all' && <p className="article-library-hint">{en ? 'Kept in this browser, without an account. Clearing browser data removes this list.' : 'Хранятся в этом браузере, без регистрации. При очистке данных браузера список удалится.'}</p>}
      {shown.length ? <>
        <ArticleCards articles={shown.slice(0, 9)} locale={locale} />
        {shown.length > 9 && <details className="article-library-more" key={`${view}:${query}`}>
          <summary>{en ? 'Show more' : 'Показать ещё'} ({shown.length - 9})</summary>
          <ArticleCards articles={shown.slice(9)} locale={locale} />
        </details>}
      </> : <div className="article-library-empty">
        <p>{terms.length ? (en ? 'No matches yet. Try a shorter word, such as “light” or “watering”.' : 'По этому запросу ничего не нашлось. Попробуйте слово покороче: «свет» или «полив».')
          : view === 'saved' ? (en ? 'Tap the bookmark on a card or in an article. Your favourites will be waiting here.' : 'Нажмите на закладку в карточке или внутри статьи. Всё полезное будет ждать вас здесь.')
            : (en ? 'The last 12 articles you open will appear here.' : 'Здесь появятся последние 12 статей, которые вы откроете.')}</p>
        <Link to={getPublicPath('articles', locale)}>{en ? 'Browse topics' : 'Выбрать рубрику'}</Link>
      </div>}
    </section>
  </div>;
}

export default ArticlesListPage;
