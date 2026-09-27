import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as api from '../../api/selfService';
import AppConnections from './AppConnections';

vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => ({}) }));
vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn() }));
vi.mock('../../api/selfService', () => ({
  archiveCoordinator: vi.fn(), createCoordinator: vi.fn(),
  fetchCoordinators: vi.fn(), rotateCoordinatorCredentials: vi.fn(),
}));

afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe('AppConnections', () => {
  it('skryvaet odnorazovye MQTT dannye ot zapisi Webvisor, sohranyaya ih dlya vladelca', async () => {
    api.fetchCoordinators.mockResolvedValue([]);
    api.createCoordinator.mockResolvedValue({
      coordinator: { id: 'synthetic-coordinator' },
      setup: {
        username: 'synthetic-user', password: 'synthetic-secret',
        base_topic: 'gh/z2m/synthetic-user',
        configuration_yaml: 'synthetic-config', secret_yaml: 'synthetic-secret-file',
      },
    });
    render(<MemoryRouter><AppConnections /></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: 'Создать', exact: true }));
    await screen.findByRole('heading', { name: 'Сохраните конфигурацию сейчас' });
    for (const value of ['synthetic-user', 'synthetic-secret', 'gh/z2m/synthetic-user']) {
      const element = screen.getByText(value);
      expect(element).toBeVisible();
      expect(element.closest('.ym-hide-content')).not.toBeNull();
    }
    expect(screen.getByRole('button', { name: 'configuration.yaml' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'secret.yaml' })).toBeEnabled();
  });
});
