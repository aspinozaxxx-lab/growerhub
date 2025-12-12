import React from 'react';
import './Typography.css';

export function Title({ level = 2, children, className }) {
  const Tag = level === 3 ? 'h3' : 'h2';
  return (
    <Tag className={['gh-title', `gh-title--${level}`, className || ''].filter(Boolean).join(' ')}>
      {children}
    </Tag>
  );
}

export function Text({ as = 'p', tone = 'default', children, className }) {
  const Tag = as;
  return (
    <Tag className={['gh-text', `gh-text--${tone}`, className || ''].filter(Boolean).join(' ')}>
      {children}
    </Tag>
  );
}

export function Caption({ children, className }) {
  return (
    <span className={['gh-caption', className || ''].filter(Boolean).join(' ')}>
      {children}
    </span>
  );
}
