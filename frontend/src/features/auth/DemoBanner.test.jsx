import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { useAuth } from './AuthContext';
import DemoBanner from './DemoBanner';

vi.mock('./AuthContext', () => ({ useAuth: vi.fn() }));

afterEach(() => { cleanup(); vi.clearAllMocks(); vi.unstubAllGlobals(); });

it('kompaktnyj banner ostavlyaet dostupnymi sohranenie i podtverzhdenie sbrosa', () => {
  const resetDemo = vi.fn();
  const leaveDemo = vi.fn();
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
  useAuth.mockReturnValue({ demoActive: true, demoSession: { saved: false }, resetDemo, leaveDemo });
  render(<MemoryRouter><DemoBanner /></MemoryRouter>);
  expect(screen.queryByRole('link', { name: 'Сохранить демоферму' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /Демо/ }));
  const dialog = screen.getByRole('dialog');
  expect(within(dialog).getByRole('link', { name: 'Сохранить демоферму' })).toHaveAttribute('href', '/app/demo/?save=1');
  expect(within(dialog).getByRole('link', { name: 'Устройства и условия среды' })).toHaveAttribute('href', '/app/demo-tools/');
  fireEvent.click(within(dialog).getByRole('button', { name: 'Восстановить демоферму' }));
  const confirmation = within(dialog).getByRole('group', { name: 'Подтверждение сброса' });
  fireEvent.click(within(confirmation).getByRole('button', { name: 'Отмена' }));
  expect(resetDemo).not.toHaveBeenCalled();
  expect(leaveDemo).not.toHaveBeenCalled();
});
