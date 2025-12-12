import React from 'react';
import './Button.css';

function Button({
  variant = 'secondary',
  size = 'md',
  type = 'button',
  disabled = false,
  isLoading = false,
  className,
  onClick,
  children,
  ...rest
}) {
  const classes = [
    'gh-btn',
    `gh-btn--${variant}`,
    `gh-btn--${size}`,
    disabled || isLoading ? 'is-disabled' : '',
    className || '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      type={type}
      className={classes}
      onClick={onClick}
      disabled={disabled || isLoading}
      {...rest}
    >
      {isLoading ? '…' : children}
    </button>
  );
}

export default Button;
