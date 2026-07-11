import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { JournalEntryCard } from './AppPlantJournal';

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
