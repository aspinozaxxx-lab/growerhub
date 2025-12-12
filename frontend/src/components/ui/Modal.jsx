import React from 'react';
import './Modal.css';
import Button from './Button';

function Modal({
  isOpen,
  title,
  children,
  footer,
  onClose,
  size = 'md',
  closeLabel = 'Закрыть',
  disableOverlayClose = false,
}) {
  if (!isOpen) {
    return null;
  }

  const handleOverlayClick = () => {
    if (!disableOverlayClose) {
      onClose?.();
    }
  };

  const handlePanelClick = (event) => {
    event.stopPropagation();
  };

  return (
    <div className="gh-modal" role="dialog" aria-modal="true" aria-label={title || closeLabel}>
      <div className="gh-modal__overlay" onClick={handleOverlayClick} />
      <div className={`gh-modal__panel gh-modal__panel--${size}`} onClick={handlePanelClick}>
        <header className="gh-modal__header">
          {title ? <div className="gh-modal__title">{title}</div> : <div />}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="gh-modal__close"
            onClick={onClose}
            aria-label={closeLabel}
          >
            ×
          </Button>
        </header>
        <div className="gh-modal__body">
          {children}
        </div>
        {footer ? <div className="gh-modal__footer">{footer}</div> : null}
      </div>
    </div>
  );
}

export default Modal;
