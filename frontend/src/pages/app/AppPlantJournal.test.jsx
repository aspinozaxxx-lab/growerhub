import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AppPlantJournal, { JournalEntryCard } from './AppPlantJournal';
import { fetchPlant } from '../../api/plants';
import { careRequest } from '../../api/care';
vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => ({ token: 'token' }) }));
vi.mock('../../api/plants', () => ({ fetchPlant: vi.fn(), createPlant: vi.fn(), updatePlant: vi.fn(), deletePlant: vi.fn() }));
vi.mock('../../api/care', () => ({ careRequest: vi.fn(), uploadCarePhoto: vi.fn() }));
vi.mock('../../api/plantJournal', () => ({ downloadPlantJournalMarkdown: vi.fn(), downloadJournalPhotoBlob: vi.fn() }));

describe('Plant care journal', () => {
  const entries = [
    { action: 'note', editable: true, entry: { id: 2, type: 'note', text: 'Новый лист', eventAt: '2026-10-04T08:00:00', photos: [] } },
    { action: 'repotting', editable: true, entry: { id: 1, type: 'other', text: 'Новый горшок', eventAt: '2026-10-01T08:00:00', photos: [] } },
  ];
  beforeEach(() => {
    fetchPlant.mockResolvedValue({ id: 5, name: 'Монстера', planted_at: null });
    careRequest.mockImplementation(async path => path.startsWith('/journal?') ? { items: entries, hasMore: false } : []);
  });
  afterEach(() => { cleanup(); vi.resetAllMocks(); });
  function open() { render(<MemoryRouter initialEntries={['/app/plants/5/journal/']}><Routes><Route path="/app/plants/:plantId/journal/" element={<AppPlantJournal />} /></Routes></MemoryRouter>); }
  it('pokazyvaet lentu raznyh dnej i ne vydumyvaet datu posadki', async () => {
    open(); expect(await screen.findByText('Новый лист')).toBeInTheDocument();
    expect(screen.getByText('Новый горшок')).toBeInTheDocument();
    expect(screen.getByText('Дата посадки не указана')).toBeInTheDocument();
  });
  it('peredajot poisk i filtry na server', async () => {
    open(); await screen.findByText('Новый лист');
    fireEvent.change(screen.getByLabelText('Поиск в журнале'), { target: { value: 'горшок' } });
    fireEvent.change(screen.getByLabelText('Вид ухода'), { target: { value: 'repotting' } });
    await waitFor(() => expect(careRequest).toHaveBeenCalledWith(expect.stringContaining('action=repotting')));
    expect(careRequest.mock.calls.some(([path]) => path.includes(new URLSearchParams({ query: 'горшок' }).toString()))).toBe(true);
  });
  it('redaktiruet datu i tekst ruchnoj zapisi', async () => {
    open(); await screen.findByText('Новый горшок');
    fireEvent.click(screen.getAllByRole('button', { name: 'Редактировать', exact: true })[1]);
    fireEvent.change(screen.getByLabelText('Дата и время'), { target: { value: '2026-10-02T11:00' } });
    fireEvent.change(screen.getByLabelText('Заметка'), { target: { value: 'Горшок побольше' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить', exact: true }));
    await waitFor(() => expect(careRequest).toHaveBeenCalledWith('/entries/1', 'PATCH', expect.objectContaining({ text: 'Горшок побольше', eventAt: '2026-10-02T08:00:00' })));
  });
});

describe('JournalEntryCard watering details', () => {
  afterEach(() => cleanup());

  it('pokazyvaet dlitelnost rezhim prichinu i nullable obem', () => {
    render(
      <JournalEntryCard
        entry={{
          id: 1,
          type: 'watering',
          event_at: '2026-07-11T12:00:00',
          watering_details: {
            water_volume_l: null,
            duration_s: 300,
            mode: 'until_leak',
            completion_reason: 'leak',
          },
          photos: [],
        }}
        onEdit={vi.fn()}
        photoCache={{}}
        setPhotoCache={vi.fn()}
        token="token"
      />,
    );

    expect(screen.getByText('Объём не рассчитан')).toBeInTheDocument();
    expect(screen.getByText('Длительность: 5 мин')).toBeInTheDocument();
    expect(screen.getByText('Режим: До протечки')).toBeInTheDocument();
    expect(screen.getByText('Остановлен по протечке')).toBeInTheDocument();
  });
});
