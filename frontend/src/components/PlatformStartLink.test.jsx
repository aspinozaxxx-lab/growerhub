import React from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PlatformStartLink from './PlatformStartLink';
import { SELF_SERVICE_PUBLIC_ENABLED } from '../domain/siteConfig';
import { trackProductGoal } from '../utils/analytics';

const authState = vi.hoisted(() => ({
  current: { status: 'unauthorized', user: null, accountStatus: 'unauthorized', accountUser: null },
}));

vi.mock('../features/auth/AuthContext', () => ({
  useAuth: () => authState.current,
}));
vi.mock('../utils/analytics', () => ({ trackProductGoal: vi.fn() }));
vi.mock('../domain/siteConfig', async (importOriginal) => ({
  ...await importOriginal(), SELF_SERVICE_PUBLIC_ENABLED: true,
}));

describe('PlatformStartLink', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    authState.current = { status: 'unauthorized', user: null, accountStatus: 'unauthorized', accountUser: null };
  });

  it('skryvaet CTA posle zaversheniya onboardinga', () => {
    authState.current = {
      status: 'authorized',
      user: { onboarding_completed: true },
      accountStatus: 'authorized',
      accountUser: { onboarding_completed: true },
    };
    const { container } = render(
      <MemoryRouter>
        <PlatformStartLink placement="test" />
      </MemoryRouter>,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('predlagaet prodolzhit nastrojku nezavershivshemu polzovatelju', () => {
    authState.current = {
      status: 'authorized',
      user: { onboarding_completed: false },
      accountStatus: 'authorized',
      accountUser: { onboarding_completed: false },
    };
    render(
      <MemoryRouter>
        <PlatformStartLink placement="test" />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Продолжить настройку' }))
      .toHaveAttribute('href', '/app/onboarding/');
  });

  it('sohranyaet publichnyj CTA do vosstanovleniya sessii dlya sovpadeniya SSR', () => {
    authState.current = { status: 'loading', user: null, accountStatus: 'loading', accountUser: null };
    const { container } = render(
      <MemoryRouter>
        <PlatformStartLink placement="test" />
      </MemoryRouter>,
    );
    expect(container.querySelector('a')).toHaveAttribute('href', SELF_SERVICE_PUBLIC_ENABLED ? '/app/login/?lang=ru&redirect=%2Fapp%2Fonboarding%2F' : '/kak-nachat/');
  });

  it('predlagaet gostju demo podkljuchit svoi ustrojstva i vyhodit iz demo po kliku', () => {
    const leaveDemo = vi.fn();
    authState.current = {
      status: 'authorized', user: { role: 'demo', onboarding_completed: true },
      accountStatus: 'unauthorized', accountUser: null, demoActive: true, leaveDemo,
    };
    render(<MemoryRouter><PlatformStartLink placement="home_hero">Подключить свои устройства</PlatformStartLink></MemoryRouter>);
    const link = screen.getByRole('link', { name: 'Подключить свои устройства' });
    expect(link).toHaveAttribute('href', '/app/login/?lang=ru&redirect=%2Fapp%2Fonboarding%2F');
    fireEvent.click(link);
    expect(leaveDemo).toHaveBeenCalledTimes(1);
    expect(trackProductGoal).toHaveBeenCalledWith('demo_real_setup_start', { placement: 'home_hero' });
  });
});
