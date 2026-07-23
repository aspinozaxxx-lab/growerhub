import {
  findStaticRoute,
  getArticlePath,
  getClusterPath,
  getPublicLocale,
  getPublicPath,
} from '../domain/localizedRoutes';
import { getArticleBySlug, getArticleTranslation } from './articles';
import { getArticleClusterById, getArticleClusterBySlug } from './articleClusters';

export const getLocalizedPathPair = (pathname = '/') => {
  const staticRoute = findStaticRoute(pathname);
  if (staticRoute) {
    return {
      ru: getPublicPath(staticRoute.routeId, 'ru'),
      en: getPublicPath(staticRoute.routeId, 'en'),
    };
  }

  const locale = getPublicLocale(pathname);
  const articleMatch = pathname.match(/^\/(?:en\/)?articles\/([^/]+)\/?$/);
  if (articleMatch) {
    const article = getArticleBySlug(articleMatch[1], locale);
    if (article) {
      const ru = getArticleTranslation(article, 'ru');
      const en = getArticleTranslation(article, 'en');
      if (ru && en) {
        return {
          ru: getArticlePath(ru, 'ru'),
          en: getArticlePath(en, 'en'),
        };
      }
    }
  }

  const clusterMatch = pathname.match(/^\/(?:en\/)?articles\/clusters\/([^/]+)\/?$/);
  if (clusterMatch) {
    const cluster = getArticleClusterBySlug(clusterMatch[1], locale);
    if (cluster) {
      const ru = getArticleClusterById(cluster.id, 'ru');
      const en = getArticleClusterById(cluster.id, 'en');
      if (ru && en) {
        return {
          ru: getClusterPath(ru, 'ru'),
          en: getClusterPath(en, 'en'),
        };
      }
    }
  }

  return {
    ru: getPublicPath('home', 'ru'),
    en: getPublicPath('home', 'en'),
  };
};
