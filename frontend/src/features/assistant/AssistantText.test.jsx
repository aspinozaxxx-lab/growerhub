import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AssistantText from './AssistantText';
import { MemoryRouter } from 'react-router-dom';

describe('assistant answer text', () => {
  it('shows emphasis and lists without exposing Markdown punctuation', () => {
    render(<AssistantText text={'## Farm\n\n**Three** zones.\n\n- First\n- Second'} />);
    expect(screen.getByText('Three').tagName).toBe('STRONG');
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });
  it('keeps provider HTML, images and links inert', () => {
    const { container } = render(<AssistantText text={'<img src="https://remote.invalid/x" onerror="alert(1)">\n\n[Click](javascript:alert%281%29)\n\n![Leaf](https://remote.invalid/tracker)'} />);
    expect(container.querySelector('img, a, script, iframe')).toBeNull();
    expect(container.textContent).toContain('Leaf');
    expect(container.textContent).toContain('<img');
  });
  it('opens only known application pages and rule links without leaving the app mode', () => {
    render(<MemoryRouter><AssistantText text={'[Сценарий](https://growerhub.ru/app/automations/?rule=c61d4fc7-2ea1-4930-ba83-58139d678218) и [Уведомления](/app/settings/notifications/)'} /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'Сценарий' })).toHaveAttribute('href', '/app/automations/?rule=c61d4fc7-2ea1-4930-ba83-58139d678218');
    expect(screen.getByRole('link', { name: 'Уведомления' })).toHaveAttribute('href', '/app/settings/notifications/');
  });
  it('does not activate external, API, credential, redirect or unrecognised parameter links', () => {
    const { container } = render(<MemoryRouter><AssistantText text={'[Внешняя](https://remote.invalid/app/) [API](/api/automation/rules) [Секрет](/app/?token=secret) [Переход](/app/?redirect=https://remote.invalid) [Вход](https://name:secret@growerhub.ru/app/) [Чужой путь](/app/admin/users/)'} /></MemoryRouter>);
    expect(container.querySelector('a')).toBeNull();
  });
});
