import {
  GOOGLE_ANALYTICS_ID,
  METRIKA_ID,
} from '../domain/siteConfig';
import { getCurrentLocale } from '../locales/i18n';
import { getAuthMode } from '../api/client';

const METRIKA_SCRIPT_ID = 'yandex-metrika-script';
const GOOGLE_ANALYTICS_SCRIPT_ID = 'google-analytics-script';
const INTERNAL_VISIT_KEY = 'gh_analytics_internal_visit';

const isInternalVisit = () => {
  if (typeof window === 'undefined') return false;
  if (window.__growerHubInternalVisit) return true;

  const params = new URLSearchParams(window.location.search);
  let internal = params.get('utm_source') === 'qa' && params.get('utm_medium') === 'internal';
  try {
    internal = internal || window.sessionStorage.getItem(INTERNAL_VISIT_KEY) === '1';
    if (internal) window.sessionStorage.setItem(INTERNAL_VISIT_KEY, '1');
  } catch {
    // Translitem: bez storage priznak proverki zhivet do perezagruzki vkladki.
  }
  if (internal) window.__growerHubInternalVisit = true;
  return internal;
};

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

export const initAnalytics = ({ enabled = typeof window !== 'undefined'
  && !['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname) } = {}) => {
  if (!enabled || isInternalVisit()) return;
  initMetrika();
  initGoogleAnalytics();
};

export const trackTelegramContact = (placement) => {
  trackProductGoal('telegram_contact', { placement });
};

const PRODUCT_GOALS = new Set([
  'platform_start',
  'signup_complete',
  'login_view',
  'sso_start',
  'coordinator_created',
  'coordinator_connected',
  'first_device_seen',
  'zone_created',
  'automation_enabled',
  'telegram_contact',
  'telegram_channel_open',
  'demo_open',
  'demo_ready',
  'demo_explore',
  'demo_action',
  'demo_save',
  'demo_reset',
  'demo_exit',
  'demo_real_setup_start',
]);

const ALLOWED_GOAL_PARAMS = new Set([
  'placement',
  'page_path',
  'step',
  'connection_mode',
  'provider',
  'scenario_type',
  'locale',
  'action',
  'mode',
]);

export const trackProductGoal = (goal, params = {}) => {
  if (!PRODUCT_GOALS.has(goal) || typeof window === 'undefined' || isInternalVisit()) {
    return false;
  }

  const mode = getAuthMode();
  if (mode === 'demo' && ['coordinator_created', 'coordinator_connected', 'first_device_seen', 'zone_created', 'automation_enabled'].includes(goal)) return false;
  const safeParams = {
    mode,
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
  if (typeof window === 'undefined' || isInternalVisit()) {
    return false;
  }

  let sent = false;
  if (typeof window.ym === 'function') {
    window.ym(METRIKA_ID, 'hit', url, {
      referer,
      title,
      params: { mode: getAuthMode() },
    });
    sent = true;
  }
  if (typeof window.gtag === 'function') {
    const googleParams = {
      page_location: url,
      page_title: title,
      mode: getAuthMode(),
    };
    if (referer) googleParams.page_referrer = referer;
    window.gtag('event', 'page_view', googleParams);
    sent = true;
  }
  return sent;
};

export const trackProductGoalOnce = (goal, params = {}, eventKey = goal) => {
  if (typeof window === 'undefined') return false;

  const storageKey = `gh_analytics_${getAuthMode()}_${eventKey}`;
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
