import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CareReminders from './CareReminders';
import { careRequest } from '../../api/care';

vi.mock('../../api/care', () => ({ careRequest: vi.fn() }));
const reminder = { id: 7, plantId: 42, plantName: 'Монстера', action: 'inspection', title: 'Проверить новый лист', dueAt: '2026-10-08T09:00:00', repeatDays: 3, repeatMode: 'calendar', occurrenceKey: 'current-occurrence' };

describe('Care reminders', () => {
  beforeEach(() => { careRequest.mockResolvedValue(null); });
  afterEach(() => { cleanup(); vi.resetAllMocks(); });

  it('sohranyaet rastenie pri izmenenii iz obshchego spiska del', async () => {
    const changed = vi.fn();
    render(<MemoryRouter><CareReminders reminders={[reminder]} onChanged={changed} /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Изменить' }));
    fireEvent.change(screen.getByLabelText('Название'), { target: { value: 'Осмотреть листья' } });
    fireEvent.input(screen.getByLabelText('Дата и время'), { target: { value: '2026-10-09T10:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить' }));
    await waitFor(() => expect(careRequest).toHaveBeenCalledWith('/reminders/7', 'PATCH', expect.objectContaining({ plantId: 42, title: 'Осмотреть листья', dueAt: '2026-10-09T07:00:00', repeatDays: 3, repeatMode: 'calendar' })));
    await waitFor(() => expect(changed).toHaveBeenCalledOnce());
  });

  it('novoe napominanie ne kopiruet predydushchee', async () => {
    render(<MemoryRouter><CareReminders plantId={42} reminders={[reminder]} onChanged={vi.fn()} /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Изменить' }));
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'Напомнить' }));
    expect(screen.getByLabelText('Название')).toHaveValue('Проверить грунт и при необходимости полить');
    expect(screen.getByLabelText('Повторять через дней (необязательно)')).toHaveValue(null);
  });

  it('otmechaet konkretnyj povtor i obnovlyaet spisok', async () => {
    render(<MemoryRouter><CareReminders reminders={[reminder]} onChanged={vi.fn()} /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Выполнено' }));
    await waitFor(() => expect(careRequest).toHaveBeenCalledWith('/reminders/7/done', 'POST', { occurrenceKey: 'current-occurrence', dueAt: null }));
    expect(careRequest).toHaveBeenCalledOnce();
  });
});
