import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
    delete window.__growerHubInternalVisit;
    delete window.__growerHubMetrikaInitialized;
    delete window.__growerHubGoogleAnalyticsInitialized;
    document.getElementById('google-analytics-script')?.remove();
    document.getElementById('yandex-metrika-script')?.remove();
    window.history.replaceState({}, '', '/app/onboarding/');
    window.ym = vi.fn();
    window.gtag = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('ne zagruzhaet schetchiki i ne schitaet pomechennyj proverochnyj vizit', () => {
    window.history.replaceState({}, '', '/?utm_source=qa&utm_medium=internal');
    initAnalytics({ enabled: true });

    expect(document.getElementById('google-analytics-script')).toBeNull();
    expect(document.getElementById('yandex-metrika-script')).toBeNull();
    expect(trackProductGoalOnce('demo_ready')).toBe(false);
    expect(trackPageView({ url: window.location.href, title: 'Демоферма' })).toBe(false);
    expect(window.ym).not.toHaveBeenCalled();
    expect(window.gtag).not.toHaveBeenCalled();
    expect(window.sessionStorage.getItem('gh_analytics_account_demo_ready')).toBeNull();
  });

  it('sohranyaet isklyuchenie pri perehodah i perezagruzke bez utm', () => {
    window.history.replaceState({}, '', '/app/demo/?utm_source=qa&utm_medium=internal');
    expect(trackProductGoal('demo_open')).toBe(false);
    window.history.replaceState({}, '', '/app/');
    delete window.__growerHubInternalVisit;

    initAnalytics({ enabled: true });
    expect(trackProductGoal('demo_ready')).toBe(false);
    expect(trackProductGoal('demo_action', { action: 'watering_start' })).toBe(false);
    expect(trackPageView({ url: window.location.href, title: 'Обзор' })).toBe(false);
    expect(document.getElementById('google-analytics-script')).toBeNull();
    expect(window.ym).not.toHaveBeenCalled();
    expect(window.gtag).not.toHaveBeenCalled();
  });

  it('ne isklyuchaet obychnye kampanii s chastichno sovpadayushchej metkoj', () => {
    window.history.replaceState({}, '', '/?utm_source=qa&utm_medium=referral');
    expect(trackProductGoal('demo_ready')).toBe(true);
    window.history.replaceState({}, '', '/?utm_source=newsletter&utm_medium=internal');
    expect(trackProductGoal('demo_action')).toBe(true);
    expect(window.ym).toHaveBeenCalledTimes(2);
    expect(window.gtag).toHaveBeenCalledTimes(2);
  });

  it('ne schitaet proverochnye dejstviya pri blokirovke storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    window.history.replaceState({}, '', '/?utm_source=qa&utm_medium=internal');
    initAnalytics({ enabled: true });
    window.history.replaceState({}, '', '/app/');

    expect(trackProductGoal('demo_ready')).toBe(false);
    expect(trackPageView({ url: window.location.href, title: 'Обзор' })).toBe(false);
    expect(window.ym).not.toHaveBeenCalled();
    expect(window.gtag).not.toHaveBeenCalled();
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
      mode: 'account',
    });
    expect(window.gtag).toHaveBeenCalledWith('event', 'first_device_seen', {
      placement: 'onboarding',
      page_path: '/app/onboarding/',
      step: 'device_detected',
      connection_mode: 'bridge',
      locale: getCurrentLocale(),
      mode: 'account',
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

    initAnalytics({ enabled: true });

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
      params: { mode: 'account' },
    });
    expect(window.gtag).toHaveBeenCalledWith('event', 'page_view', {
      page_location: 'https://growerhub.ru/kak-nachat/',
      page_referrer: 'https://growerhub.ru/',
      page_title: 'Как начать',
      mode: 'account',
    });
  });
});
