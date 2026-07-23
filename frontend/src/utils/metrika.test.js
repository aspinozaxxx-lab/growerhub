import { beforeEach, describe, expect, it, vi } from 'vitest';
import { METRIKA_ID } from '../domain/siteConfig';
import { getCurrentLocale } from '../locales/i18n';
import { trackProductGoal, trackProductGoalOnce } from './metrika';

describe('product metrika goals', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    window.history.replaceState({}, '', '/app/onboarding/');
    window.ym = vi.fn();
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
  });

  it('ne dubliruet odno sobytie v tekushchej sessii', () => {
    expect(trackProductGoalOnce('signup_complete', { step: 'sso_callback' })).toBe(true);
    expect(trackProductGoalOnce('signup_complete', { step: 'sso_callback' })).toBe(false);
    expect(window.ym).toHaveBeenCalledTimes(1);
  });

  it('ignoriruet neizvestnye celi', () => {
    expect(trackProductGoal('unknown_goal')).toBe(false);
    expect(window.ym).not.toHaveBeenCalled();
  });
});
