import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EditDeviceModal from './EditDeviceModal';

describe('EditDeviceModal', () => {
  afterEach(() => cleanup());

  it('ne pokazyvaet redaktor privyazok nasosa', () => {
    render(
      <EditDeviceModal
        device={{
          id: 1,
          name: 'Контроллер',
          sensors: [{ id: 2, label: 'Почва', type: 'SOIL_MOISTURE', bound_plants: [] }],
          pumps: [{ id: 3, label: 'Скрытый насос', bound_plants: [] }],
        }}
        plants={[{ id: 10, name: 'Томат', plant_group: { name: 'Овощи' } }]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
        token="token"
      />,
    );

    expect(screen.getByRole('dialog', { name: 'Привязки датчиков' })).toBeInTheDocument();
    expect(screen.getByText('Датчики')).toBeInTheDocument();
    expect(screen.queryByText('Насосы')).not.toBeInTheDocument();
    expect(screen.queryByText('Скрытый насос')).not.toBeInTheDocument();
  });
});
