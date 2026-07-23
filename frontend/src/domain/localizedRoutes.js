export const DEFAULT_LOCALE = 'ru';
export const SUPPORTED_LOCALES = ['ru', 'en'];
export const ENGLISH_PREFIX = '/en';

export const PUBLIC_ROUTES = Object.freeze({
  home: { ru: '/', en: '/en/' },
  farmAutomation: { ru: '/avtomatizatsiya-mini-fermy/', en: '/en/farm-automation/' },
  gettingStarted: { ru: '/kak-nachat/', en: '/en/getting-started/' },
  equipment: { ru: '/oborudovanie/', en: '/en/equipment/' },
  equipmentCoordinators: {
    ru: '/oborudovanie/zigbee-koordinator/',
    en: '/en/equipment/zigbee-coordinators/',
  },
  equipmentSensors: {
    ru: '/oborudovanie/datchiki/',
    en: '/en/equipment/sensors/',
  },
  equipmentSockets: {
    ru: '/oborudovanie/zigbee-rozetki/',
    en: '/en/equipment/zigbee-smart-plugs/',
  },
  equipmentPump: {
    ru: '/oborudovanie/nasos-dlya-poliva/',
    en: '/en/equipment/irrigation-pump/',
  },
  articles: { ru: '/articles/', en: '/en/articles/' },
  about: { ru: '/about/', en: '/en/about/' },
  privacy: { ru: '/privacy/', en: '/en/privacy/' },
  terms: { ru: '/terms/', en: '/en/terms/' },
});

const ROUTE_LOOKUP = new Map(
  Object.entries(PUBLIC_ROUTES).flatMap(([routeId, variants]) => (
    SUPPORTED_LOCALES.map((locale) => [
      variants[locale],
      { routeId, locale },
    ])
  )),
);

export const normalizeLocale = (locale) => (
  SUPPORTED_LOCALES.includes(locale) ? locale : DEFAULT_LOCALE
);

export const ensureTrailingSlash = (pathname = '/') => {
  const cleanPath = String(pathname).split(/[?#]/, 1)[0] || '/';
  if (cleanPath === '/') return '/';
  return cleanPath.endsWith('/') ? cleanPath : `${cleanPath}/`;
};

export const getPublicLocale = (pathname = '/') => (
  ensureTrailingSlash(pathname) === '/en/' || ensureTrailingSlash(pathname).startsWith('/en/')
    ? 'en'
    : 'ru'
);

export const isAppPath = (pathname = '/') => (
  ensureTrailingSlash(pathname) === '/app/' || ensureTrailingSlash(pathname).startsWith('/app/')
);

export const getPublicPath = (routeId, locale = DEFAULT_LOCALE) => {
  const route = PUBLIC_ROUTES[routeId];
  if (!route) {
    throw new Error(`Unknown public route: ${routeId}`);
  }
  return route[normalizeLocale(locale)];
};

export const findStaticRoute = (pathname = '/') => ROUTE_LOOKUP.get(ensureTrailingSlash(pathname)) || null;

export const getStaticAlternatePath = (pathname = '/') => {
  const match = findStaticRoute(pathname);
  if (!match) return null;
  const alternateLocale = match.locale === 'ru' ? 'en' : 'ru';
  return getPublicPath(match.routeId, alternateLocale);
};

export const getArticlePath = (article, locale = DEFAULT_LOCALE) => {
  if (!article?.slug) return getPublicPath('articles', locale);
  const prefix = normalizeLocale(locale) === 'en' ? '/en/articles/' : '/articles/';
  return `${prefix}${article.slug}/`;
};

export const getClusterPath = (cluster, locale = DEFAULT_LOCALE) => {
  if (!cluster?.slug) return getPublicPath('articles', locale);
  const prefix = normalizeLocale(locale) === 'en'
    ? '/en/articles/clusters/'
    : '/articles/clusters/';
  return `${prefix}${cluster.slug}/`;
};

export const getPlatformStartPath = (locale = DEFAULT_LOCALE) => {
  const params = new URLSearchParams({
    lang: normalizeLocale(locale),
    redirect: '/app/onboarding/',
  });
  return `/app/login/?${params.toString()}`;
};
