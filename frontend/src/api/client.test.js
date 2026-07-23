import { describe, expect, it } from 'vitest';
import { normalizeApiErrorMessage } from './client';

describe('normalizeApiErrorMessage', () => {
  it('не показывает транслит из ответа API', () => {
    expect(normalizeApiErrorMessage(
      'Self-service poka vyklyuchen do zavershenija proverki',
      { status: 503 },
    )).toBe('Сервис временно недоступен');
  });

  it('сохраняет понятное сообщение на кириллице', () => {
    expect(normalizeApiErrorMessage('Координатор не найден', { status: 404 }))
      .toBe('Координатор не найден');
  });

  it('использует контекстный текст вместо английской ошибки', () => {
    expect(normalizeApiErrorMessage('Device not found', {
      status: 404,
      fallback: 'Не удалось загрузить устройство',
    })).toBe('Не удалось загрузить устройство');
  });
});
