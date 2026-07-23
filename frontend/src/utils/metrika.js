import { METRIKA_ID } from '../domain/siteConfig';
import { getCurrentLocale } from '../locales/i18n';

const METRIKA_SCRIPT_ID = 'yandex-metrika-script';

export const initMetrika = () => {
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
  if (
    !PRODUCT_GOALS.has(goal)
    || typeof window === 'undefined'
    || typeof window.ym !== 'function'
  ) {
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
  window.ym(METRIKA_ID, 'reachGoal', goal, safeParams);
  return true;
};

export const trackProductGoalOnce = (goal, params = {}, eventKey = goal) => {
  if (typeof window === 'undefined') return false;

  const storageKey = `gh_metrika_${eventKey}`;
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
