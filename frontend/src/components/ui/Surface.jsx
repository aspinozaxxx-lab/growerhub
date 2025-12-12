import React from 'react';
import './Surface.css';

function Surface({ variant = 'card', padding = 'md', className, children, as: Component = 'div' }) {
  const classes = [
    'gh-surface',
    `gh-surface--${variant}`,
    `gh-surface--p-${padding}`,
    className || '',
  ]
    .filter(Boolean)
    .join(' ');

  return <Component className={classes}>{children}</Component>;
}

export function Divider({ className }) {
  return <div className={['gh-divider', className || ''].filter(Boolean).join(' ')} role="separator" />;
}

export default Surface;
