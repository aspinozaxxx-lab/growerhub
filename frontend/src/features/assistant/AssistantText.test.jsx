import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AssistantText from './AssistantText';

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
});
