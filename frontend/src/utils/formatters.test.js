import { afterEach, describe, expect, it } from 'vitest';
import {
  DEFAULT_UI_TIME_ZONE,
  formatDateKeyYYYYMMDD,
  formatTimeHHMM,
  setUiTimeZone,
  startOfUiDayMs,
  zonedDateTimeInputToUtc,
} from './formatters';

describe('timezone formatters', () => {
  afterEach(() => {
    setUiTimeZone(DEFAULT_UI_TIME_ZONE);
  });

  it('pokazyvaet UTC timestamp v timezone polzovatelja', () => {
    setUiTimeZone('Asia/Yekaterinburg');
    expect(formatTimeHHMM('2026-07-30T06:43:00')).toBe('11:43');
  });

  it('preobrazuet datetime-local iz timezone polzovatelja v UTC', () => {
    setUiTimeZone('Europe/Moscow');
    expect(zonedDateTimeInputToUtc('2026-07-30T09:43')?.toISOString())
      .toBe('2026-07-30T06:43:00.000Z');
  });

  it('uchityvaet perehod na letnee vremja v granice sutok', () => {
    setUiTimeZone('Europe/Berlin');
    const dayStart = startOfUiDayMs('2026-03-29T12:00:00Z');
    expect(new Date(dayStart).toISOString()).toBe('2026-03-28T23:00:00.000Z');
    expect(formatDateKeyYYYYMMDD(dayStart)).toBe('2026-03-29');
  });
});
