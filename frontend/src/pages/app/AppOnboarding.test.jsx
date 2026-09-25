import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../../api/selfService';
import { trackProductGoal } from '../../utils/analytics';
import { changeLocale, loadAppTranslations } from '../../locales/i18n';
import AppOnboarding from './AppOnboarding';
import AppOverview from './AppOverview';

const { loadCurrentUser } = vi.hoisted(() => ({ loadCurrentUser: vi.fn() }));

vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => ({ loadCurrentUser }) }));
vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn(), trackProductGoalOnce: vi.fn() }));
vi.mock('../../features/sensors/SensorStatsContext', () => ({
  useSensorStatsContext: () => ({ openSensorStats: vi.fn() }),
}));
vi.mock('../../api/selfService', () => ({
  createCoordinator: vi.fn(), completeOnboarding: vi.fn(), createGreenhouse: vi.fn(),
  createUserFarm: vi.fn(), enablePermitJoin: vi.fn(), fetchCoordinatorOverview: vi.fn(),
  fetchCoordinators: vi.fn(), fetchFarmsOverview: vi.fn(), fetchOnboardingStatus: vi.fn(),
  replaceGreenhouseSlots: vi.fn(), rotateCoordinatorCredentials: vi.fn(),
  fetchManualWateringGreenhouseStatistics: vi.fn(), stopManualWatering: vi.fn(),
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

  it.each([
    {
      name: 'odin pochvennyj datchik bez rele i temperatury',
      properties: ['soil_moisture'],
      choices: [{ label: 'Влажность почвы', property: 'soil_moisture', role: 'SOIL_MOISTURE_SENSOR' }],
    },
    {
      name: 'dva pokazatelya odnogo ustrojstva bez avtomaticheskogo naznacheniya ostalnyh',
      properties: ['temperature', 'humidity', 'soil_moisture'],
      choices: [
        { label: 'Влажность воздуха', property: 'humidity', role: 'AIR_HUMIDITY_SENSOR' },
        { label: 'Влажность почвы', property: 'soil_moisture', role: 'SOIL_MOISTURE_SENSOR' },
      ],
    },
    { name: 'yavnyj propusk naznacheniya', properties: ['soil_moisture'], choices: [] },
  ])('zavershaet pervoe podklyuchenie: $name', async ({ properties, choices }) => {
    api.fetchCoordinators.mockResolvedValue([coordinator]);
    api.fetchOnboardingStatus.mockResolvedValue({
      ...freshStatus, step: 'CREATE_ZONE', coordinator_count: 1,
      coordinator_connected: true, first_device_seen: true,
    });
    api.fetchCoordinatorOverview.mockResolvedValue({ devices: [{
      ieee_address: 'synthetic-sensor', friendly_name: 'Мой датчик',
      metrics: properties.map((property) => ({ property })), controls: [],
    }] });
    const readings = { temperature: 24, humidity: 62, soil_moisture: 43 };
    let farmOverview = { farms: [] };
    api.fetchFarmsOverview.mockImplementation(async () => farmOverview);
    api.createUserFarm.mockImplementation(async () => {
      farmOverview = { farms: [{ id: 10, name: 'Моя ферма', enabled: true, greenhouses: [] }] };
      return farmOverview;
    });
    api.createGreenhouse.mockImplementation(async () => {
      farmOverview.farms[0].greenhouses.push({ id: 20, name: 'Первая теплица', enabled: true, slots: [] });
      return farmOverview;
    });
    api.replaceGreenhouseSlots.mockImplementation(async (_, slots) => {
      farmOverview.farms[0].greenhouses[0].slots = slots.map((slot, index) => ({
        ...slot, id: index + 1, ready: true, current_value: readings[slot.zigbee_property],
      }));
      return farmOverview;
    });
    api.completeOnboarding.mockResolvedValue({});
    render(
      <MemoryRouter initialEntries={['/app/onboarding/']}>
        <Routes>
          <Route path="/app/onboarding/" element={<AppOnboarding />} />
          <Route path="/app/" element={<AppOverview />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: /Уже работает Zigbee2MQTT/u }));
    await screen.findByRole('heading', { name: 'Создайте первую теплицу' });
    expect(screen.queryByLabelText('Розетка или реле для света')).not.toBeInTheDocument();
    if (!properties.includes('temperature')) {
      expect(screen.queryByLabelText('Датчик температуры')).not.toBeInTheDocument();
    }
    for (const choice of choices) {
      const select = screen.getByLabelText(choice.label);
      const option = within(select).getByRole('option', { name: `Мой датчик · ${choice.property}` });
      fireEvent.change(select, { target: { value: option.value } });
    }
    fireEvent.click(screen.getByRole('button', { name: 'Создать теплицу и открыть обзор' }));
    expect(await screen.findByRole('heading', { name: 'Обзор' })).toBeInTheDocument();
    for (const choice of choices) {
      expect(screen.getByRole('button', { name: `Открыть статистику: ${choice.label}` }))
        .toHaveTextContent(`${readings[choice.property]} %`);
    }
    expect(api.completeOnboarding).toHaveBeenCalledOnce();
    expect(api.createGreenhouse).toHaveBeenCalledExactlyOnceWith(10, { name: 'Первая теплица', enabled: true });
    if (choices.length) {
      expect(api.replaceGreenhouseSlots).toHaveBeenCalledExactlyOnceWith(20, choices.map((choice) => ({
        role: choice.role, source_type: 'ZIGBEE_DEVICE',
        zigbee_coordinator_id: coordinator.id, zigbee_ieee_address: 'synthetic-sensor',
        zigbee_property: choice.property,
      })));
    } else {
      expect(api.replaceGreenhouseSlots).not.toHaveBeenCalled();
    }
    expect(api.enablePermitJoin).not.toHaveBeenCalled();
    expect(api.rotateCoordinatorCredentials).not.toHaveBeenCalled();
  });
});
