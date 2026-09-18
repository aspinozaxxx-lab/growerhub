import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AppPlantJournal, { JournalEntryCard } from './AppPlantJournal';
import { fetchPlants } from '../../api/plants';
import { createPlantJournalEntry, fetchPlantJournal, updatePlantJournalEntry } from '../../api/plantJournal';

vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => ({ token: 'token' }) }));
vi.mock('../../api/plants', () => ({ fetchPlants: vi.fn() }));
vi.mock('../../api/plantJournal', () => ({
  fetchPlantJournal: vi.fn(),
  createPlantJournalEntry: vi.fn(),
  updatePlantJournalEntry: vi.fn(),
  downloadPlantJournalMarkdown: vi.fn(),
  downloadJournalPhotoBlob: vi.fn(),
}));

describe('AppPlantJournal selected day', () => {
  const older = { id: 1, type: 'note', text: 'Первый спелый томат', event_at: '2026-09-16T10:00:00', photos: [] };
  const latest = { id: 2, type: 'note', text: 'Сладкие плоды без трещин', event_at: '2026-09-16T22:00:00', photos: [] };

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-09-18T09:00:00Z'));
    fetchPlants.mockResolvedValue([{ id: 5, name: 'Томат черри', planted_at: '2026-09-01T08:00:00' }]);
    fetchPlantJournal.mockResolvedValue([latest, older]);
  });

  afterEach(() => {
    cleanup();
    vi.resetAllMocks();
    vi.useRealTimers();
  });

  function openJournal() {
    render(<MemoryRouter initialEntries={['/app/plants/5/journal/']}>
      <Routes><Route path="/app/plants/:plantId/journal/" element={<AppPlantJournal />} /></Routes>
    </MemoryRouter>);
  }

  it('srazu pokazyvaet poslednyuyu zapis po lokalnoj date nezavisimo ot poryadka API', async () => {
    openJournal();
    expect(await screen.findByText(latest.text)).toBeInTheDocument();
    expect(screen.getByText(/Записи за 17 сентября/)).toBeInTheDocument();
    expect(screen.queryByText(older.text)).not.toBeInTheDocument();
  });

  it('otkryvaet segodnya dlya pustogo zhurnala', async () => {
    fetchPlantJournal.mockResolvedValue([]);
    openJournal();
    expect(await screen.findByText('Нет записей за выбранную дату')).toBeInTheDocument();
    expect(screen.getByText(/Записи за 18 сентября/)).toBeInTheDocument();
  });

  it('posle dobavleniya na druguyu datu pokazyvaet sohranennuyu zapis', async () => {
    openJournal();
    await screen.findByText(latest.text);
    fireEvent.click(screen.getByRole('button', { name: 'Добавить запись' }));
    fireEvent.change(screen.getByLabelText('Дата'), { target: { value: '2026-09-18' } });
    fireEvent.change(screen.getByLabelText('Текст / комментарий'), { target: { value: 'Посадить этот сорт снова' } });
    const saved = { id: 3, type: 'note', text: 'Посадить этот сорт снова', event_at: '2026-09-18T09:00:00', photos: [] };
    createPlantJournalEntry.mockResolvedValue(saved);
    fetchPlantJournal.mockResolvedValue([saved, latest, older]);
    fireEvent.click(screen.getByRole('button', { name: 'Добавить', exact: true }));
    await waitFor(() => expect(screen.queryByLabelText('Текст / комментарий')).not.toBeInTheDocument());
    expect(screen.getByText(saved.text)).toBeInTheDocument();
    expect(screen.getByText(/Записи за 18 сентября/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Текст / комментарий')).not.toBeInTheDocument();
  });

  it('sohranyaet vybrannyj proshlyj den pri redaktirovanii', async () => {
    openJournal();
    await screen.findByText(latest.text);
    fireEvent.click(screen.getByText('16', { selector: '.journal-calendar__date-number' }));
    fireEvent.click(screen.getByTitle('Редактировать'));
    fireEvent.change(screen.getByLabelText('Текст / комментарий'), { target: { value: 'Первый сбор: 200 г' } });
    const saved = { ...older, text: 'Первый сбор: 200 г' };
    updatePlantJournalEntry.mockResolvedValue(saved);
    fetchPlantJournal.mockResolvedValue([latest, saved]);
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить', exact: true }));
    await waitFor(() => expect(screen.queryByLabelText('Текст / комментарий')).not.toBeInTheDocument());
    expect(screen.getByText(saved.text)).toBeInTheDocument();
    expect(screen.getByText(/Записи за 16 сентября/)).toBeInTheDocument();
    expect(screen.queryByText(latest.text)).not.toBeInTheDocument();
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
