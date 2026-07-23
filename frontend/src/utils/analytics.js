import {
  GOOGLE_ANALYTICS_ID,
  METRIKA_ID,
} from '../domain/siteConfig';
import { getCurrentLocale } from '../locales/i18n';

const METRIKA_SCRIPT_ID = 'yandex-metrika-script';
const GOOGLE_ANALYTICS_SCRIPT_ID = 'google-analytics-script';

const initMetrika = () => {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  window.ym = window.ym || function ym(...args) {
    window.ym.a = window.ym.a || [];
    window.ym.a.push(args);
  };
  window.ym.l = window.ym.l || Date.now();

  if (!document.getElementById(METRIKA_SCRIPT_ID)) {
    const script = document.createElement('script');
    script.id = METRIKA_SCRIPT_ID;
    script.async = true;
    script.src = `https://mc.yandex.ru/metrika/tag.js?id=${METRIKA_ID}`;
    document.head.appendChild(script);
  }

  if (!window.__growerHubMetrikaInitialized) {
    window.ym(METRIKA_ID, 'init', {
      defer: true,
      ssr: true,
      webvisor: true,
      clickmap: true,
      accurateTrackBounce: true,
      trackLinks: true,
    });
    window.__growerHubMetrikaInitialized = true;
  }
};

const initGoogleAnalytics = () => {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  window.dataLayer = window.dataLayer || [];
  window.gtag = window.gtag || function gtag() {
    window.dataLayer.push(arguments);
  };

  if (!document.getElementById(GOOGLE_ANALYTICS_SCRIPT_ID)) {
    const script = document.createElement('script');
    script.id = GOOGLE_ANALYTICS_SCRIPT_ID;
    script.async = true;
    script.src = `https://www.googletagmanager.com/gtag/js?id=${GOOGLE_ANALYTICS_ID}`;
    document.head.appendChild(script);
  }

  if (!window.__growerHubGoogleAnalyticsInitialized) {
    window.gtag('js', new Date());
    window.gtag('config', GOOGLE_ANALYTICS_ID, {
      allow_google_signals: false,
      allow_ad_personalization_signals: false,
      send_page_view: false,
    });
    window.__growerHubGoogleAnalyticsInitialized = true;
  }
};

export const initAnalytics = () => {
  initMetrika();
  initGoogleAnalytics();
};

export const trackTelegramContact = (placement) => {
  trackProductGoal('telegram_contact', { placement });
};

const PRODUCT_GOALS = new Set([
  'platform_start',
  'signup_complete',
  'coordinator_created',
  'coordinator_connected',
  'first_device_seen',
  'zone_created',
  'automation_enabled',
  'telegram_contact',
]);

const ALLOWED_GOAL_PARAMS = new Set([
  'placement',
  'page_path',
  'step',
  'connection_mode',
  'scenario_type',
  'locale',
]);

export const trackProductGoal = (goal, params = {}) => {
  if (!PRODUCT_GOALS.has(goal) || typeof window === 'undefined') {
    return false;
  }

  const safeParams = {
    page_path: window.location.pathname,
    locale: getCurrentLocale(),
  };
  Object.entries(params).forEach(([key, value]) => {
    if (ALLOWED_GOAL_PARAMS.has(key) && value !== undefined && value !== null && value !== '') {
      safeParams[key] = String(value);
    }
  });

  let sent = false;
  if (typeof window.ym === 'function') {
    window.ym(METRIKA_ID, 'reachGoal', goal, safeParams);
    sent = true;
  }
  if (typeof window.gtag === 'function') {
    window.gtag('event', goal, safeParams);
    sent = true;
  }
  return sent;
};

export const trackPageView = ({ url, referer, title }) => {
  if (typeof window === 'undefined') {
    return false;
  }

  let sent = false;
  if (typeof window.ym === 'function') {
    window.ym(METRIKA_ID, 'hit', url, {
      referer,
      title,
    });
    sent = true;
  }
  if (typeof window.gtag === 'function') {
    const googleParams = {
      page_location: url,
      page_title: title,
    };
    if (referer) googleParams.page_referrer = referer;
    window.gtag('event', 'page_view', googleParams);
    sent = true;
  }
  return sent;
};

export const trackProductGoalOnce = (goal, params = {}, eventKey = goal) => {
  if (typeof window === 'undefined') return false;

  const storageKey = `gh_analytics_${eventKey}`;
  try {
    if (window.sessionStorage.getItem(storageKey) === '1') return false;
  } catch {
    // Translitem: pri nedostupnom sessionStorage sobytie vse ravno otpravljaetsja.
  }

  const sent = trackProductGoal(goal, params);
  if (!sent) return false;

  try {
    window.sessionStorage.setItem(storageKey, '1');
  } catch {
    // Translitem: blokirovka storage ne dolzhna lomat' pol'zovatel'skij scenarij.
  }
  return true;
};
