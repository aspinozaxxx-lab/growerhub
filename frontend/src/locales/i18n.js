import i18n from 'i18next';
import commonEn from './common.en.json';
import commonRu from './common.ru.json';
import publicEn from './public.en.json';
import publicRu from './public.ru.json';
import {
  DEFAULT_LOCALE,
  getPublicLocale,
  isAppPath,
  normalizeLocale,
} from '../domain/localizedRoutes';

export const LOCALE_STORAGE_KEY = 'growerhub_locale';

const readStoredLocale = () => {
  if (typeof window === 'undefined') return null;
  try {
    const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    return stored === 'ru' || stored === 'en' ? stored : null;
  } catch {
    return null;
  }
};

const readQueryLocale = () => {
  if (typeof window === 'undefined') return null;
  const value = new URLSearchParams(window.location.search).get('lang');
  return value === 'ru' || value === 'en' ? value : null;
};

export const detectInitialLocale = () => {
  if (typeof window === 'undefined') return DEFAULT_LOCALE;
  if (!isAppPath(window.location.pathname)) {
    return getPublicLocale(window.location.pathname);
  }
  return readQueryLocale()
    || readStoredLocale()
    || (window.navigator.language?.toLowerCase().startsWith('en') ? 'en' : DEFAULT_LOCALE);
};

const initialLocale = detectInitialLocale();

if (
  typeof window !== 'undefined'
  && isAppPath(window.location.pathname)
  && !window.location.pathname.startsWith('/app/admin/')
  && readQueryLocale()
) {
  try {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, initialLocale);
  } catch {
    // Translitem: nedostupnyj storage ne dolzhen lomat' zagruzku prilozheniya.
  }
}

i18n.init({
  lng: initialLocale,
  fallbackLng: DEFAULT_LOCALE,
  defaultNS: 'common',
  ns: ['common', 'public'],
  keySeparator: false,
  nsSeparator: false,
  showSupportNotice: false,
  initImmediate: false,
  interpolation: {
    escapeValue: false,
  },
  resources: {
    ru: {
      common: commonRu,
      public: publicRu,
    },
    en: {
      common: commonEn,
      public: publicEn,
    },
  },
});

if (typeof document !== 'undefined') {
  document.documentElement.lang = initialLocale;
}

let appTranslationsPromise = null;

export const loadAppTranslations = async () => {
  if (!appTranslationsPromise) {
    appTranslationsPromise = Promise.all([
      import('./app.ru.json'),
      import('./app.en.json'),
    ]).then(([ruModule, enModule]) => {
      i18n.addResourceBundle('ru', 'app', ruModule.default, true, true);
      i18n.addResourceBundle('en', 'app', enModule.default, true, true);
    });
  }
  return appTranslationsPromise;
};

export const getCurrentLocale = () => normalizeLocale(i18n.resolvedLanguage || i18n.language);

export const getStoredLocale = () => readStoredLocale() || DEFAULT_LOCALE;

export const getIntlLocale = (locale = getCurrentLocale()) => (
  normalizeLocale(locale) === 'en' ? 'en-GB' : 'ru-RU'
);

export const rememberLocale = (locale) => {
  const normalized = normalizeLocale(locale);
  if (typeof window !== 'undefined') {
    try {
      window.localStorage.setItem(LOCALE_STORAGE_KEY, normalized);
    } catch {
      // Translitem: nedostupnyj storage ne dolzhen lomat' perekljuchenie jazyka.
    }
  }
  return normalized;
};

export const changeLocale = async (locale, { remember = true } = {}) => {
  const normalized = remember ? rememberLocale(locale) : normalizeLocale(locale);
  await i18n.changeLanguage(normalized);
  if (typeof document !== 'undefined') {
    document.documentElement.lang = normalized;
  }
  return normalized;
};

export const translateCommon = (key, options) => i18n.t(key, { ns: 'common', ...options });
export const translatePublic = (key, options) => i18n.t(key, { ns: 'public', ...options });
export const translateApp = (key, options) => i18n.t(key, { ns: 'app', ...options });

export default i18n;
