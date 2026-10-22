import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import RuleExplanation from './RuleExplanation';

afterEach(cleanup);

const definition = {
  version: 3, name: 'Обдув при свете', scopeType: 'BOX', scopeId: 2, cooldownSeconds: 0,
  trigger: { type: 'repeat_while', seconds: 1800, first: 'immediate', onConditionLoss: 'release' },
  condition: { type: 'sensor', resourceId: 10, op: 'eq', value: 'ON', maxAgeSeconds: 120 },
  actions: [{ type: 'hold', resourceId: 11, value: 'ON', durationSeconds: 180, priority: 50 }],
};

describe('RuleExplanation coordinated holds', () => {
  it('explains recurrence, confirmation and release before the user enables it', () => {
    render(<RuleExplanation definition={definition} resourceNames={{ 10: 'Свет', 11: 'Вытяжка' }} />);
    expect(screen.getByText(/Каждые 30 мин/)).toBeInTheDocument();
    expect(screen.getByText('Первый запуск сразу при выполнении условия.')).toBeInTheDocument();
    expect(screen.getByText('Если условие пропадёт или данные устареют, удержание сразу отменится.')).toBeInTheDocument();
    expect(screen.getByText(/Удерживать состояние · Вытяжка: Включено · 3:00 · Приоритет: 50/)).toBeInTheDocument();
    expect(screen.getByText(/от подтверждения устройства/)).toBeInTheDocument();
    expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
  });
  it('distinguishes a delayed first run and a cycle that may finish after condition loss', () => {
    render(<RuleExplanation definition={{ ...definition, trigger: { ...definition.trigger, first: 'after_interval', onConditionLoss: 'finish' } }} />);
    expect(screen.getByText('Первый запуск через один период после выполнения условия.')).toBeInTheDocument();
    expect(screen.getByText(/Устаревшие данные отменяют удержание/)).toBeInTheDocument();
  });
});
