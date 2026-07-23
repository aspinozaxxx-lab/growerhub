import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  GOOGLE_ANALYTICS_ID,
  METRIKA_ID,
} from '../domain/siteConfig';
import { getCurrentLocale } from '../locales/i18n';
import {
  initAnalytics,
  trackPageView,
  trackProductGoal,
  trackProductGoalOnce,
} from './analytics';

describe('product analytics goals', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    window.history.replaceState({}, '', '/app/onboarding/');
    window.ym = vi.fn();
    window.gtag = vi.fn();
  });

  it('peredajot tolko razreshennye nepersonalnye parametry', () => {
    const sent = trackProductGoal('first_device_seen', {
      placement: 'onboarding',
      step: 'device_detected',
      connection_mode: 'bridge',
      user_id: '42',
      ieee: 'secret',
    });

    expect(sent).toBe(true);
    expect(window.ym).toHaveBeenCalledWith(METRIKA_ID, 'reachGoal', 'first_device_seen', {
      placement: 'onboarding',
      page_path: '/app/onboarding/',
      step: 'device_detected',
      connection_mode: 'bridge',
      locale: getCurrentLocale(),
    });
    expect(window.gtag).toHaveBeenCalledWith('event', 'first_device_seen', {
      placement: 'onboarding',
      page_path: '/app/onboarding/',
      step: 'device_detected',
      connection_mode: 'bridge',
      locale: getCurrentLocale(),
    });
  });

  it('ne dubliruet odno sobytie v tekushchej sessii', () => {
    expect(trackProductGoalOnce('signup_complete', { step: 'sso_callback' })).toBe(true);
    expect(trackProductGoalOnce('signup_complete', { step: 'sso_callback' })).toBe(false);
    expect(window.ym).toHaveBeenCalledTimes(1);
    expect(window.gtag).toHaveBeenCalledTimes(1);
  });

  it('ignoriruet neizvestnye celi', () => {
    expect(trackProductGoal('unknown_goal')).toBe(false);
    expect(window.ym).not.toHaveBeenCalled();
    expect(window.gtag).not.toHaveBeenCalled();
  });

  it('sozdaet sovmestimuju s gtag.js ochered kommand', () => {
    delete window.gtag;
    window.dataLayer = [];
    delete window.__growerHubGoogleAnalyticsInitialized;
    document.getElementById('google-analytics-script')?.remove();

    initAnalytics();

    expect(Object.prototype.toString.call(window.dataLayer[0])).toBe('[object Arguments]');
    expect(Array.from(window.dataLayer[0])[0]).toBe('js');
    expect(Array.from(window.dataLayer[1])).toEqual([
      'config',
      GOOGLE_ANALYTICS_ID,
      {
        allow_google_signals: false,
        allow_ad_personalization_signals: false,
        send_page_view: false,
      },
    ]);
  });

  it('otpravljaet odin virtualnyj prosmotr v obe sistemy', () => {
    expect(trackPageView({
      url: 'https://growerhub.ru/kak-nachat/',
      referer: 'https://growerhub.ru/',
      title: 'Как начать',
    })).toBe(true);

    expect(window.ym).toHaveBeenCalledWith(METRIKA_ID, 'hit', 'https://growerhub.ru/kak-nachat/', {
      referer: 'https://growerhub.ru/',
      title: 'Как начать',
    });
    expect(window.gtag).toHaveBeenCalledWith('event', 'page_view', {
      page_location: 'https://growerhub.ru/kak-nachat/',
      page_referrer: 'https://growerhub.ru/',
      page_title: 'Как начать',
    });
  });
});
