export const SITE_NAME = 'GrowerHub';
export const SITE_URL = 'https://growerhub.ru';
export const METRIKA_ID = 110256357;
export const TELEGRAM_CHANNEL_URL = 'https://t.me/growerhub_info';
export const TELEGRAM_DIRECT_URL = `${TELEGRAM_CHANNEL_URL}?direct`;
export const DEFAULT_OG_IMAGE = '/og-growerhub.svg';
export const GITHUB_RELEASES_URL = 'https://github.com/aspinozaxxx-lab/growerhub/releases';
export const PLATFORM_START_PATH = '/app/login/?redirect=/app/onboarding/';

const viteSelfServiceEnabled = typeof import.meta.env !== 'undefined'
  && import.meta.env?.VITE_SELF_SERVICE_ENABLED === 'true';
const nodeSelfServiceEnabled = globalThis.process?.env?.VITE_SELF_SERVICE_ENABLED === 'true';

export const SELF_SERVICE_PUBLIC_ENABLED = viteSelfServiceEnabled || nodeSelfServiceEnabled;

export const toCanonicalPath = (pathname = '/') => {
  const cleanPath = String(pathname).split(/[?#]/, 1)[0] || '/';

  if (cleanPath === '/') {
    return '/';
  }

  return cleanPath.endsWith('/') ? cleanPath : `${cleanPath}/`;
};

export const toCanonicalUrl = (pathname = '/') => `${SITE_URL}${toCanonicalPath(pathname)}`;

export const canonicalizePublicLinksInHtml = (html = '') => String(html).replace(
  /href="(\/(?:about|avtomatizatsiya-mini-fermy|kak-nachat|oborudovanie(?:\/[^"?#]*)?|privacy|terms|articles(?:\/[^"?#]*)?))([?#][^"]*)?"/g,
  (_, pathname, suffix = '') => `href="${toCanonicalPath(pathname)}${suffix}"`,
);
