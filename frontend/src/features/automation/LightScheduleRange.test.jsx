import { useState } from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LightScheduleRange from './LightScheduleRange';
import { lightDuration, timeToMinutes } from './lightSchedule';

function Editor({ start = '06:00', end = '22:00' }) {
  const [config, setConfig] = useState({ start_time: start, end_time: end });
  return <>
    <LightScheduleRange name="Рассада" config={config} onSelect={() => {}}
      onChange={(patch) => setConfig((current) => ({ ...current, ...patch }))} />
    <output>{config.start_time} — {config.end_time}</output>
  </>;
}

describe('LightScheduleRange', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('menyaet granicy s klaviatury i sohranyaet dlitelnost pri perenose cherez polnoch', () => {
    render(<Editor />);
    fireEvent.keyDown(screen.getByRole('slider', { name: 'Начало освещения: Рассада' }), { key: 'ArrowRight', shiftKey: true });
    expect(screen.getByRole('status')).toHaveTextContent('06:15 — 22:00');
    fireEvent.keyDown(screen.getByRole('slider', { name: 'Конец освещения: Рассада' }), { key: 'ArrowLeft' });
    expect(screen.getByRole('status')).toHaveTextContent('06:15 — 21:59');
    fireEvent.keyDown(screen.getByRole('slider', { name: 'Перенести интервал: Рассада' }), { key: 'Home' });
    expect(screen.getByRole('status')).toHaveTextContent('00:00 — 15:44');
    fireEvent.keyDown(screen.getByRole('slider', { name: 'Перенести интервал: Рассада' }), { key: 'ArrowLeft' });
    expect(screen.getByRole('status')).toHaveTextContent('23:59 — 15:43');
  });

  it('prodolzhaet pointer drag posle perehoda cherez polnoch i otmenyaet ego', () => {
    vi.stubGlobal('PointerEvent', MouseEvent);
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({ width: 1440 });
    const { container } = render(<Editor start="23:00" end="07:00" />);
    const range = container.querySelector('.light-range');
    fireEvent.pointerDown(screen.getAllByRole('slider', { name: 'Перенести интервал: Рассада' })[0], { button: 0, clientX: 500 });
    fireEvent.pointerMove(range, { clientX: 590 });
    expect(screen.getByRole('status')).toHaveTextContent('00:30 — 08:30');
    fireEvent.pointerMove(range, { clientX: 650 });
    expect(screen.getByRole('status')).toHaveTextContent('01:30 — 09:30');
    fireEvent.pointerCancel(range);
    expect(screen.getByRole('status')).toHaveTextContent('23:00 — 07:00');
  });

  it('ravenstvo vremeni oznachaet sutki kak v backend', () => {
    render(<Editor start="00:00" end="00:00" />);
    expect(screen.getByText('24 ч света')).toBeInTheDocument();
    expect(lightDuration(timeToMinutes('21:00'), timeToMinutes('07:00'))).toBe(600);
    expect(timeToMinutes('24:00')).toBeNull();
    expect(timeToMinutes('')).toBeNull();
  });
});
