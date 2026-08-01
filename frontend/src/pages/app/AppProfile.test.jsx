import React from 'react';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from '@testing-library/react';
import {
  MemoryRouter,
  Route,
  Routes,
} from 'react-router-dom';
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';
import { fetchAuthMethods } from '../../api/auth';
import AppProfile from './AppProfile';

const authState = vi.hoisted(() => ({ current: null }));

vi.mock('../../api/auth', () => ({
  changePassword: vi.fn(),
  fetchAuthMethods: vi.fn(),
  linkSsoMethod: vi.fn(),
  setLocalLogin: vi.fn(),
  unlinkAuthMethod: vi.fn(),
  updateCurrentProfile: vi.fn(),
}));

vi.mock('../../features/auth/AuthContext', () => ({
  useAuth: () => authState.current,
}));

function renderProfile() {
  return render(
    <MemoryRouter initialEntries={['/app/settings/profile/']}>
      <Routes>
        <Route path="/app/settings/profile/" element={<AppProfile />} />
        <Route path="/app/admin/dashboard/" element={<div>Admin dashboard target</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function buildAuthState(role) {
  return {
    user: {
      id: 1,
      email: 'user@example.com',
      username: 'grower',
      role,
      is_active: true,
      timezone: 'Europe/Moscow',
    },
    token: 'test-token',
    status: 'authorized',
    logout: vi.fn(),
    setCurrentUser: vi.fn(),
  };
}

describe('AppProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchAuthMethods.mockResolvedValue({});
    authState.current = buildAuthState('admin');
  });

  afterEach(() => {
    cleanup();
  });

  it('otkryvaet admin dashboard i stilizuet timezone select', async () => {
    renderProfile();

    const timezoneSelect = await screen.findByRole('combobox', { name: 'Часовой пояс' });
    expect(timezoneSelect).toHaveClass('profile-timezone-select', 'gh-control');

    const profileCard = screen.getByText('Роль').closest('.profile-card');
    const adminButton = within(profileCard).getByRole('button', { name: 'Администрирование' });
    expect(adminButton.closest('.app-page-header')).toBeNull();

    fireEvent.click(adminButton);
    expect(screen.getByText('Admin dashboard target')).toBeInTheDocument();
  });

  it('ne pokazyvaet knopku administrirovaniya obychnomu polzovatelyu', async () => {
    authState.current = buildAuthState('user');
    renderProfile();

    await screen.findByRole('combobox', { name: 'Часовой пояс' });
    expect(screen.queryByRole('button', { name: 'Администрирование' })).not.toBeInTheDocument();
  });
});
