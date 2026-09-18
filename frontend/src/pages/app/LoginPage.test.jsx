import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import LoginPage from './LoginPage';

const auth = vi.hoisted(() => ({
  initial: 'unauthorized',
  clearError: vi.fn(),
  setRedirectAfterLogin: vi.fn(),
  consumeRedirectAfterLogin: vi.fn(() => '/app/'),
}));
vi.mock('../../features/auth/AuthContext', () => ({
  useAuth: () => {
    const [accountStatus, setStatus] = React.useState(auth.initial);
    return {
      ...auth, accountStatus,
      loginWithPassword: async () => {
        setStatus('authorized');
        return { success: true };
      },
    };
  },
}));
vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn(), trackProductGoalOnce: vi.fn() }));
afterEach(() => { cleanup(); auth.initial = 'unauthorized'; vi.clearAllMocks(); });

function entry(url) {
  render(<MemoryRouter initialEntries={[url]}><Routes>
    <Route path="/app/login/" element={<LoginPage />} />
    <Route path="/app/demo/" element={<div>Save destination</div>} />
    <Route path="/app/" element={<div>Account destination</div>} />
  </Routes></MemoryRouter>);
}

it('vozvrashchaet posle parolnogo vhoda k sohraneniyu demo odin raz', async () => {
  entry('/app/login/?redirect=%2Fapp%2Fdemo%2F%3Fsave%3D1');
  expect(document.title).toBe('Начать работу с GrowerHub');
  expect(document.head.querySelector('meta[name="robots"]').content).toBe('noindex,nofollow');
  fireEvent.click(screen.getByText('Вход по паролю для существующих аккаунтов'));
  fireEvent.change(screen.getByLabelText('Электронная почта'), { target: { value: 'test@example.invalid' } });
  fireEvent.change(screen.getByLabelText('Пароль'), { target: { value: 'local-test' } });
  fireEvent.click(screen.getByRole('button', { name: 'Войти', exact: true }));
  await waitFor(() => expect(screen.getByText('Save destination')).toBeInTheDocument());
  expect(auth.consumeRedirectAfterLogin).toHaveBeenCalledTimes(1);
  expect(auth.setRedirectAfterLogin).toHaveBeenCalledWith('/app/demo/?save=1');
});

it('sohranyaet namerenie pri uzhe vosstanovlennoj sessii', async () => {
  auth.initial = 'authorized';
  entry('/app/login/?redirect=%2Fapp%2Fdemo%2F%3Fsave%3D1');
  await waitFor(() => expect(screen.getByText('Save destination')).toBeInTheDocument());
  expect(auth.consumeRedirectAfterLogin).toHaveBeenCalledTimes(1);
});

it('otklonyaet vneshnij redirect', async () => {
  auth.initial = 'authorized';
  entry('/app/login/?redirect=https%3A%2F%2Fexample.invalid%2F');
  await waitFor(() => expect(screen.getByText('Account destination')).toBeInTheDocument());
});
