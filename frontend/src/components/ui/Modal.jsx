import React from 'react';
import './Modal.css';

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
          <button type="button" className="gh-modal__close" onClick={onClose} aria-label={closeLabel}>
            ×
          </button>
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
