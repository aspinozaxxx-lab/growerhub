import React from 'react';
import './Surface.css';

function Surface({ variant = 'card', padding = 'md', className, children, as = 'div' }) {
  const classes = [
    'gh-surface',
    `gh-surface--${variant}`,
    `gh-surface--p-${padding}`,
    className || '',
  ]
    .filter(Boolean)
    .join(' ');

  return React.createElement(as, { className: classes }, children);
}

export function Divider({ className }) {
  return <div className={['gh-divider', className || ''].filter(Boolean).join(' ')} role="separator" />;
}

export default Surface;
