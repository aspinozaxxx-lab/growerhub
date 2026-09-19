import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../../api/selfService';
import { trackProductGoal } from '../../utils/analytics';
import { changeLocale, loadAppTranslations } from '../../locales/i18n';
import AppOnboarding from './AppOnboarding';

const { loadCurrentUser } = vi.hoisted(() => ({ loadCurrentUser: vi.fn() }));

vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => ({ loadCurrentUser }) }));
vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn(), trackProductGoalOnce: vi.fn() }));
vi.mock('../../api/selfService', () => ({
  createCoordinator: vi.fn(), completeOnboarding: vi.fn(), createGreenhouse: vi.fn(),
  createUserFarm: vi.fn(), enablePermitJoin: vi.fn(), fetchCoordinatorOverview: vi.fn(),
  fetchCoordinators: vi.fn(), fetchFarmsOverview: vi.fn(), fetchOnboardingStatus: vi.fn(),
  replaceGreenhouseSlots: vi.fn(), rotateCoordinatorCredentials: vi.fn(),
}));

const coordinator = { id: 'synthetic-coordinator', name: 'Test connection' };
const freshStatus = {
  step: 'CREATE_COORDINATOR', coordinator_count: 0, coordinator_connected: false,
  first_device_seen: false, zone_created: false, completed: false,
};
const renderPage = () => render(<MemoryRouter><AppOnboarding /></MemoryRouter>);

describe('AppOnboarding', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.fetchOnboardingStatus.mockResolvedValue(freshStatus);
    api.fetchCoordinators.mockResolvedValue([]);
    api.fetchCoordinatorOverview.mockResolvedValue({ devices: [] });
    loadCurrentUser.mockResolvedValue({});
  });
  afterEach(async () => {
    cleanup();
    await changeLocale('ru', { remember: false });
  });

  it('anglijskij vhod pokazyvaet perevod i vedet k anglijskomu katalogu', async () => {
    await loadAppTranslations();
    await changeLocale('en', { remember: false });
    renderPage();
    expect(await screen.findByRole('heading', { name: 'What do you already have?' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'See compatible devices' })).toHaveAttribute('href', '/en/equipment/');
    expect(document.body.textContent).not.toMatch(/[\u0400-\u04ff]/u);
  });

  it('snachala vybiraet sushchestvuyushchuyu set, zatem sozdaet bridge bez instrukcii perepodklyucheniya', async () => {
    api.createCoordinator.mockImplementation(async () => {
      api.fetchCoordinators.mockResolvedValue([coordinator]);
      api.fetchOnboardingStatus.mockResolvedValue({ ...freshStatus, step: 'CONNECT_COORDINATOR', coordinator_count: 1 });
      return { coordinator, setup: {
        server: 'mqtts://example.test:8883', username: 'synthetic-user', password: 'synthetic-secret',
        client_id: 'synthetic-user', base_topic: 'gh/z2m/synthetic-user',
        configuration_yaml: 'synthetic-config', secret_yaml: 'synthetic-secret-file',
      } };
    });
    renderPage();
    const bridge = await screen.findByRole('button', { name: /Уже работает Zigbee2MQTT/u });
    expect(bridge).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: /Новая установка/u })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByRole('button', { name: 'Создать подключение' })).not.toBeInTheDocument();
    expect(api.createCoordinator).not.toHaveBeenCalled();

    fireEvent.click(bridge);
    fireEvent.click(await screen.findByRole('button', { name: 'Создать подключение' }));
    expect(await screen.findByRole('heading', { name: 'Локальный MQTT' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Скачать личный bridge.conf' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Скачать configuration.yaml' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Разрешить подключение на 3 минуты' })).not.toBeInTheDocument();
    expect(api.createCoordinator).toHaveBeenCalledExactlyOnceWith('Моя ферма');
    expect(trackProductGoal).toHaveBeenCalledWith('coordinator_created', { step: 'credentials_shown', connection_mode: 'bridge' });
  });

  it('posle vozvrata ne predlagaet pryamoje podklyuchenie ili rotaciyu bez vybora sposoba', async () => {
    api.fetchCoordinators.mockResolvedValue([coordinator]);
    api.fetchOnboardingStatus.mockResolvedValue({ ...freshStatus, step: 'ADD_DEVICE', coordinator_count: 1, coordinator_connected: true });
    renderPage();
    const bridge = await screen.findByRole('button', { name: /Уже работает Zigbee2MQTT/u });
    expect(screen.queryByRole('button', { name: 'Выпустить новые данные' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Разрешить подключение на 3 минуты' })).not.toBeInTheDocument();
    fireEvent.click(bridge);
    expect(await screen.findByRole('heading', { name: 'Импортируем существующие устройства' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Выпустить новые данные' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Разрешить подключение на 3 минуты' })).not.toBeInTheDocument();
    expect(api.rotateCoordinatorCredentials).not.toHaveBeenCalled();
    expect(api.enablePermitJoin).not.toHaveBeenCalled();
  });

  it('novaya ustanovka sohranyaet vozmozhnost sopryazheniya posle yavnogo vybora', async () => {
    api.fetchCoordinators.mockResolvedValue([coordinator]);
    api.fetchOnboardingStatus.mockResolvedValue({ ...freshStatus, step: 'ADD_DEVICE', coordinator_count: 1, coordinator_connected: true });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /Новая установка/u }));
    const permit = await screen.findByRole('button', { name: 'Разрешить подключение на 3 минуты' });
    expect(api.enablePermitJoin).not.toHaveBeenCalled();
    fireEvent.click(permit);
    await waitFor(() => expect(api.enablePermitJoin).toHaveBeenCalledExactlyOnceWith(coordinator.id, 180));
  });

  it('zavershennaya nastrojka otkryvaet fermu bez predlozheniya smenit parol pri otsutstvii svyazi', async () => {
    api.fetchCoordinators.mockResolvedValue([coordinator]);
    api.fetchOnboardingStatus.mockResolvedValue({ ...freshStatus, step: 'COMPLETE', coordinator_count: 1, first_device_seen: true, zone_created: true, completed: true });
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Базовая настройка завершена' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Открыть обзор' })).toHaveAttribute('href', '/app/');
    expect(screen.getByRole('link', { name: 'Управлять подключениями' })).toHaveAttribute('href', '/app/settings/connections/');
    expect(screen.queryByRole('group', { name: 'Способ подключения' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Выпустить новые данные' })).not.toBeInTheDocument();
    expect(api.createCoordinator).not.toHaveBeenCalled();
    expect(api.rotateCoordinatorCredentials).not.toHaveBeenCalled();
    expect(api.completeOnboarding).not.toHaveBeenCalled();
  });
});
