import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import LeadCta from '../components/LeadCta';
import DemoStartLink from '../components/DemoStartLink';
import { getArticleClusterById } from '../content/articleClusters';
import {
  getArticleBySlug,
  getArticleTranslation,
  getRelatedArticles,
  loadArticleBody,
} from '../content/articles';
import {
  getArticlePath,
  getClusterPath,
  getPublicPath,
} from '../domain/localizedRoutes';
import {
  GITHUB_REPOSITORY_URL,
  DEMO_PUBLIC_ENABLED,
  ORGANIZATION_ID,
  SITE_URL,
} from '../domain/siteConfig';
import { getCurrentLocale, getIntlLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';
import NotFoundPage from './NotFoundPage';

const readStaticBody = (article) => {
  if (!article || typeof document === 'undefined') return '';
  const element = document.getElementById('growerhub-page-data');
  if (!element?.textContent) return '';
  try {
    const payload = JSON.parse(element.textContent);
    return payload.articleId === article.id && payload.locale === article.locale
      ? payload.bodyHtml || ''
      : '';
  } catch {
    return '';
  }
};

function ArticlePage({ initialArticle } = {}) {
  const { slug } = useParams();
  const locale = getCurrentLocale();
  const article = useMemo(() => getArticleBySlug(slug, locale), [locale, slug]);
  const cluster = useMemo(() => getArticleClusterById(article?.cluster, locale), [article, locale]);
  const relatedArticles = useMemo(() => getRelatedArticles(article, 4, locale), [article, locale]);
  const articleKey = article ? `${article.locale}:${article.id}` : '';
  const staticBodyHtml = useMemo(() => (initialArticle?.id === article?.id && initialArticle?.locale === locale
    ? initialArticle.bodyHtml : readStaticBody(article)), [article, initialArticle, locale]);
  const [loadedBody, setLoadedBody] = useState({ key: '', html: '', error: false });
  const bodyHtml = staticBodyHtml || (loadedBody.key === articleKey ? loadedBody.html : '');
  const bodyError = loadedBody.key === articleKey && loadedBody.error;
  const ruArticle = getArticleTranslation(article, 'ru');
  const enArticle = getArticleTranslation(article, 'en');
  const path = article ? getArticlePath(article, locale) : null;
  const demoView = {
    'avtopoliv-i-kontroller-vyrashchivaniya': 'automations',
    'home-assistant-i-diy': 'automations',
    'zhurnal-i-sovetnik-uhoda': 'plants',
    'mini-ferma-i-neskolko-boksov': 'farm',
  }[article?.cluster] || 'overview';
  const demoHint = {
    automations: translatePublic('Измените расписание света и параметры полива в готовой теплице.'),
    plants: translatePublic('Откройте растения, историю поливов и записи в журнале.'),
    farm: translatePublic('Посмотрите, как устройства и растения собраны в четыре теплицы.'),
    overview: translatePublic('Сравните показания четырёх теплиц и откройте историю любого датчика.'),
  }[demoView];

  useEffect(() => {
    if (!article || staticBodyHtml) return undefined;
    let active = true;
    loadArticleBody(article)
      .then((html) => {
        if (active) setLoadedBody({ key: articleKey, html, error: false });
      })
      .catch(() => {
        if (active) setLoadedBody({ key: articleKey, html: '', error: true });
      });
    return () => {
      active = false;
    };
  }, [article, articleKey, staticBodyHtml]);

  useSeoMeta({
    title: article ? `${article.title} — GrowerHub` : translatePublic('Статья не найдена — GrowerHub'),
    description: article?.summary || translatePublic('Запрошенная статья GrowerHub не найдена.'),
    path,
    type: article ? 'article' : 'website',
    image: article?.hero_image || undefined,
    robots: article ? 'index,follow' : 'noindex,nofollow',
    locale,
    alternatePaths: ruArticle && enArticle ? {
      ru: getArticlePath(ruArticle, 'ru'),
      en: getArticlePath(enArticle, 'en'),
    } : undefined,
    jsonLd: article ? [{
      '@context': 'https://schema.org',
      '@type': 'BlogPosting',
      headline: article.title,
      description: article.summary,
      datePublished: article.created_at,
      dateModified: article.updated_at,
      url: `${SITE_URL}${path}`,
      image: article.hero_image ? `${SITE_URL}${article.hero_image}` : undefined,
      inLanguage: locale,
      author: {
        '@type': 'Organization',
        '@id': ORGANIZATION_ID,
        name: 'GrowerHub',
        url: SITE_URL,
        sameAs: [GITHUB_REPOSITORY_URL],
      },
      publisher: {
        '@type': 'Organization',
        '@id': ORGANIZATION_ID,
        name: 'GrowerHub',
        url: SITE_URL,
      },
    }] : [],
  });

  if (!article) {
    return <NotFoundPage />;
  }

  return (
    <article className="section">
      <div className="article-meta">
        {translatePublic('Обновлено')} {new Date(article.updated_at).toLocaleDateString(getIntlLocale(locale), { timeZone: 'UTC' })}
      </div>
      <h1>{article.title}</h1>
      <p className="article-lead">{article.summary}</p>
      {cluster && (
        <Link to={getClusterPath(cluster, locale)} className="secondary-link">
          {cluster.title}
        </Link>
      )}
      {DEMO_PUBLIC_ENABLED && (
        <aside className="article-demo-invite" aria-label={translatePublic('Попробовать в GrowerHub')}>
          <p><strong>{translatePublic('Попробуйте на готовой ферме')}</strong><span>{demoHint} {translatePublic('Без регистрации и оборудования.')}</span></p>
          <DemoStartLink placement="article_intro_demo" view={demoView} className="hero-cta">{translatePublic('Открыть демо')}</DemoStartLink>
        </aside>
      )}
      {article.hero_image && !article.hero_in_body && (
        <img fetchPriority="high" decoding="async" className="article-hero-image" src={article.hero_image} alt={article.hero_alt || article.title} />
      )}
      {bodyError ? (
        <div className="article-body">{translatePublic('Не удалось загрузить материал. Обновите страницу.')}</div>
      ) : (
        <div
          className="article-body"
          // Translitem: Markdown zagruzhaetsya otdel'no ot metadannyh stranicy.
          dangerouslySetInnerHTML={{ __html: bodyHtml }}
        />
      )}
      <aside className="info-block content-section">
        <strong>{translatePublic('Редакция GrowerHub')}</strong>
        <p>
          {translatePublic('Материал сопровождается публичной историей разработки и честным описанием эксплуатационных данных. Это не означает, что каждое упомянутое устройство проверено нами.')}
        </p>
        <div className="cta-row">
          <Link className="secondary-link" to={getPublicPath('about', locale)}>
            {translatePublic('Как мы подтверждаем опыт')}
          </Link>
          <a className="secondary-link" href={GITHUB_REPOSITORY_URL} target="_blank" rel="noreferrer">
            {translatePublic('Исходный код на GitHub')}
          </a>
        </div>
      </aside>
      <LeadCta
        placement="article_bottom"
        demoView={demoView}
        title={DEMO_PUBLIC_ENABLED ? translatePublic('Попробуйте на готовой ферме') : cluster?.guide.cta.title}
        text={DEMO_PUBLIC_ENABLED ? `${demoHint} ${translatePublic('Без регистрации и оборудования.')}` : cluster?.guide.cta.text}
      />
      {relatedArticles.length > 0 && (
        <section className="related-articles">
          <h2>{translatePublic('Читайте также')}</h2>
          <div className="articles-list">
            {relatedArticles.map((relatedArticle) => (
              <article className="article-card" key={relatedArticle.slug}>
                <Link to={getArticlePath(relatedArticle, locale)}>{relatedArticle.title}</Link>
                <p>{relatedArticle.summary}</p>
              </article>
            ))}
          </div>
        </section>
      )}
      <div className="content-section">
        <Link to={getPublicPath('articles', locale)} className="secondary-link">
          {translatePublic('Назад к статьям')}
        </Link>
      </div>
    </article>
  );
}

export default ArticlePage;
