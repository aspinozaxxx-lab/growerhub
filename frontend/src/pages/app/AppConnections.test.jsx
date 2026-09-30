import { Blob as NodeBlob } from 'node:buffer';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '../../api/selfService';
import { trackProductGoal } from '../../utils/analytics';
import { changeLocale, loadAppTranslations } from '../../locales/i18n';
import AppConnections from './AppConnections';

vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => ({}) }));
vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn() }));
vi.mock('../../api/selfService', () => ({
  archiveCoordinator: vi.fn(), createCoordinator: vi.fn(),
  fetchCoordinators: vi.fn(), rotateCoordinatorCredentials: vi.fn(),
}));

const coordinator = { id: 'synthetic-coordinator', name: 'Test connection', status: 'OFFLINE' };
const setup = {
  server: 'mqtts://growerhub.ru:8883', username: 'synthetic-user', password: 'synthetic-secret',
  client_id: 'synthetic-user', base_topic: 'gh/z2m/synthetic-user',
  configuration_yaml: 'synthetic-config', secret_yaml: 'synthetic-secret-file',
};
const renderPage = () => render(<MemoryRouter><AppConnections /></MemoryRouter>);

beforeEach(() => {
  api.fetchCoordinators.mockResolvedValue([]);
  api.createCoordinator.mockResolvedValue({ coordinator, setup });
});
afterEach(async () => {
  cleanup();
  vi.restoreAllMocks();
  vi.resetAllMocks();
  vi.unstubAllGlobals();
  await changeLocale('ru', { remember: false });
});

describe('AppConnections', () => {
  it('trebuet yavnyj vybor sposoba do sozdaniya novogo podklyucheniya', async () => {
    renderPage();
    const bridge = await screen.findByRole('button', { name: /Уже работает Zigbee2MQTT/u });
    expect(bridge).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: /Новая установка/u })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByRole('button', { name: 'Создать', exact: true })).not.toBeInTheDocument();
    expect(api.createCoordinator).not.toHaveBeenCalled();
    fireEvent.click(bridge);
    expect(screen.getByText(/отдельного режима только чтения и выбора одного устройства пока нет/u)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Создать', exact: true })).toBeEnabled();
    expect(api.createCoordinator).not.toHaveBeenCalled();
  });

  it('skryvaet MQTT dannye ot Webvisor i sohranyaet novuyu USB ustanovku', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /Новая установка/u }));
    fireEvent.click(screen.getByRole('button', { name: 'Создать', exact: true }));
    await screen.findByRole('heading', { name: 'Сохраните конфигурацию подключения' });
    for (const value of ['synthetic-user', 'synthetic-secret', 'gh/z2m/synthetic-user']) {
      const element = screen.getByText(value);
      expect(element).toBeVisible();
      expect(element.closest('.ym-hide-content')).not.toBeNull();
    }
    expect(screen.getByRole('button', { name: 'Скачать configuration.yaml' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Скачать secret.yaml' })).toBeEnabled();
    expect(screen.getByRole('link', { name: 'Скачать пакет для Windows' })).toHaveAttribute('href', expect.stringContaining('windows'));
    fireEvent.click(screen.getByRole('button', { name: /^Raspberry Pi/u }));
    expect(screen.getByRole('link', { name: 'Скачать пакет для Raspberry Pi / Linux' })).toHaveAttribute('href', expect.stringContaining('linux'));
    expect(screen.queryByRole('button', { name: 'Создать', exact: true })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Скрыть навсегда' }));
    expect(screen.getByRole('button', { name: /Новая установка/u })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByRole('button', { name: 'Создать', exact: true })).not.toBeInTheDocument();
  });

  it('skachivaet lokalnyj bridge bez otpravki lokalnogo parolya v API', async () => {
    vi.stubGlobal('Blob', NodeBlob);
    class DownloadURL extends URL {}
    DownloadURL.createObjectURL = vi.fn(() => 'blob:synthetic-download');
    DownloadURL.revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', DownloadURL);
    const downloads = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function captureDownload() {
      downloads.push(this.download);
    });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /Уже работает Zigbee2MQTT/u }));
    fireEvent.click(screen.getByRole('button', { name: 'Создать', exact: true }));
    await screen.findByRole('heading', { name: 'Локальный MQTT' });
    const download = screen.getByRole('button', { name: 'Скачать личный bridge.conf' });
    expect(download).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Скачать configuration.yaml' })).not.toBeInTheDocument();
    for (const label of ['Адрес', 'Порт', 'Базовая тема Zigbee2MQTT', 'Имя пользователя', 'Пароль']) {
      expect(screen.getByLabelText(label).closest('.ym-hide-content')).not.toBeNull();
    }
    fireEvent.change(screen.getByLabelText('Адрес'), { target: { value: '192.0.2.10' } });
    fireEvent.change(screen.getByLabelText('Базовая тема Zigbee2MQTT'), { target: { value: 'plants/z2m' } });
    fireEvent.change(screen.getByLabelText('Имя пользователя'), { target: { value: 'local-test-user' } });
    fireEvent.change(screen.getByLabelText('Пароль'), { target: { value: 'local-test-secret' } });
    fireEvent.click(download);
    expect(downloads).toEqual(['bridge.conf']);
    const config = await DownloadURL.createObjectURL.mock.calls[0][0].text();
    expect(config).toContain('address 192.0.2.10:1883');
    expect(config).toContain('remote_password local-test-secret');
    expect(config).toContain('topic + in 1 relay/from-local/ plants/z2m/');
    expect(config).toContain('remote_clientid synthetic-user-local');
    expect(config).not.toContain('topic #');
    expect(api.createCoordinator).toHaveBeenCalledExactlyOnceWith('Дополнительный координатор');
    expect(api.rotateCoordinatorCredentials).not.toHaveBeenCalled();
    expect(trackProductGoal).toHaveBeenCalledExactlyOnceWith('coordinator_created', {
      placement: 'connections', step: 'credentials_shown', connection_mode: 'bridge',
    });
  });

  it('posle rotacii ne ugadyvaet sposob i daet sohranit most', async () => {
    api.fetchCoordinators.mockResolvedValue([coordinator]);
    api.rotateCoordinatorCredentials.mockResolvedValue(setup);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Новые данные доступа' }));
    await screen.findByRole('heading', { name: 'Сохраните конфигурацию подключения' });
    expect(api.rotateCoordinatorCredentials).toHaveBeenCalledExactlyOnceWith(coordinator.id);
    expect(screen.queryByRole('button', { name: 'Скачать configuration.yaml' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Скачать личный bridge.conf' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Уже работает Zigbee2MQTT/u }));
    expect(screen.getByRole('heading', { name: 'Локальный MQTT' })).toBeInTheDocument();
    expect(api.createCoordinator).not.toHaveBeenCalled();
  });

  it('otmena rotacii ne menyaet rabotayushchee podklyuchenie', async () => {
    api.fetchCoordinators.mockResolvedValue([coordinator]);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Новые данные доступа' }));
    expect(api.rotateCoordinatorCredentials).not.toHaveBeenCalled();
    expect(api.createCoordinator).not.toHaveBeenCalled();
    expect(api.archiveCoordinator).not.toHaveBeenCalled();
  });

  it('pokazyvaet obshchij vybor i instrukcii na anglijskom', async () => {
    await loadAppTranslations();
    await changeLocale('en', { remember: false });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /Zigbee2MQTT is already running/u }));
    expect(document.body.textContent).not.toMatch(/[Ѐ-ӿ]/u);
  });
});
