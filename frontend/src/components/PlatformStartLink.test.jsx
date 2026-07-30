import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PlatformStartLink from './PlatformStartLink';

const authState = vi.hoisted(() => ({
  current: { status: 'unauthorized', user: null },
}));

vi.mock('../features/auth/AuthContext', () => ({
  useAuth: () => authState.current,
}));

describe('PlatformStartLink', () => {
  afterEach(() => {
    cleanup();
    authState.current = { status: 'unauthorized', user: null };
  });

  it('skryvaet CTA posle zaversheniya onboardinga', () => {
    authState.current = {
      status: 'authorized',
      user: { onboarding_completed: true },
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
    };
    render(
      <MemoryRouter>
        <PlatformStartLink placement="test" />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Продолжить настройку' }))
      .toHaveAttribute('href', '/app/onboarding/');
  });

  it('ne pokazyvaet nevernyj CTA poka sessija zagruzhaetsja', () => {
    authState.current = { status: 'loading', user: null };
    const { container } = render(
      <MemoryRouter>
        <PlatformStartLink placement="test" />
      </MemoryRouter>,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
