import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import CodexStatus from './CodexStatus';
import { assistantRequest } from '../../api/assistant';
vi.mock('../../api/assistant', () => ({ assistantRequest: vi.fn() }));
vi.mock('../../locales/i18n', () => ({ translateApp: text => text }));
const status = { installed: '0.163.0', latest: '0.164.0', updateAvailable: true, checkedAt: new Date().toISOString() };
beforeEach(() => {
  vi.clearAllMocks();
  assistantRequest.mockImplementation(async (path, method, body) => {
    if (path === '/runtime') return status;
    if (method === 'POST') return { id: body.id, target: body.target, previous: body.expectedInstalled, phase: 'queued', updatedAt: new Date().toISOString() };
    return { enabled: true, operation: null };
  });
});
afterEach(cleanup);
test('requires a separate confirmation and binds it to the displayed release', async () => {
  render(<CodexStatus />);
  fireEvent.click(await screen.findByRole('button', { name: 'Обновить Codex' }));
  expect(assistantRequest.mock.calls.some(([, method]) => method === 'POST')).toBe(false);
  expect(screen.getByRole('alertdialog')).toHaveTextContent('0.163.0 → 0.164.0');
  fireEvent.click(screen.getByRole('button', { name: 'Запустить обновление' }));
  await screen.findByText('Обновление принято');
  expect(assistantRequest).toHaveBeenCalledWith('/runtime/update', 'POST', { id: expect.any(String), target: '0.164.0', expectedInstalled: '0.163.0' });
  expect(screen.getByRole('button', { name: 'Обновить Codex' })).toBeDisabled();
});
test('reload shows persisted rollback and never starts another operation', async () => {
  const original = assistantRequest.getMockImplementation();
  assistantRequest.mockImplementation((path, ...args) => path === '/runtime/update' ? Promise.resolve({ enabled: true,
    operation: { phase: 'rolled_back', previous: '0.163.0', installed: '0.163.0', target: '0.164.0', finishedAt: new Date().toISOString() } }) : original(path, ...args));
  render(<CodexStatus />);
  await screen.findByText('Восстановлена предыдущая версия, проверка пройдена');
  expect(assistantRequest.mock.calls.some(([, method]) => method === 'POST')).toBe(false);
});
test('forbidden accounts do not get update controls', async () => {
  assistantRequest.mockRejectedValue(new Error('forbidden'));
  const { container } = render(<CodexStatus />);
  await waitFor(() => expect(assistantRequest).toHaveBeenCalled());
  expect(container).toBeEmptyDOMElement();
});
test('a lost update response is recovered by its id without offering to send it again', async () => {
  let operation = null;
  assistantRequest.mockImplementation(async (path, method, body) => {
    if (path === '/runtime') return status;
    if (method === 'POST') {
      operation = { id: body.id, phase: 'succeeded', previous: '0.163.0', target: '0.164.0', installed: '0.164.0', finishedAt: new Date().toISOString() };
      throw new Error('Connection interrupted');
    }
    return { enabled: true, operation };
  });
  render(<CodexStatus />);
  fireEvent.click(await screen.findByRole('button', { name: 'Обновить Codex' }));
  fireEvent.click(screen.getByRole('button', { name: 'Запустить обновление' }));
  await screen.findByText('Обновление завершено, ответ помощника проверен');
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  expect(assistantRequest.mock.calls.filter(([, method]) => method === 'POST')).toHaveLength(1);
});
