import React, { useEffect, useRef } from 'react';
import './Modal.css';
import Button from './Button';
import { translateApp } from '../../locales/i18n';

function Modal({
  isOpen,
  title,
  children,
  footer,
  onClose,
  size = 'md',
  closeLabel = translateApp("Закрыть"),
  disableOverlayClose = false,
  presentation = 'dialog',
}) {
  const panelRef = useRef(null);
  const closeRef = useRef(onClose);
  useEffect(() => { closeRef.current = onClose; }, [onClose]);
  useEffect(() => {
    if (!isOpen) return undefined;
    const previousFocus = document.activeElement;
    const panel = panelRef.current;
    const focusable = () => [...panel.querySelectorAll('button, a[href], input, select, textarea')]
      .filter((element) => !element.disabled && !element.closest('[hidden]'));
    focusable()[0]?.focus();
    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        closeRef.current?.();
      }
      if (event.key !== 'Tab') return;
      const elements = focusable();
      const first = elements[0];
      const last = elements.at(-1);
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first?.focus();
      }
    };
    panel.addEventListener('keydown', onKeyDown);
    return () => {
      panel.removeEventListener('keydown', onKeyDown);
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [isOpen]);

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
    <div className={`gh-modal ${presentation === 'sheet' ? 'gh-modal--sheet' : ''}`} role="dialog" aria-modal="true" aria-label={title || closeLabel}>
      <div className="gh-modal__overlay" onClick={handleOverlayClick} />
      <div className={`gh-modal__panel gh-modal__panel--${size}`} onClick={handlePanelClick} ref={panelRef}>
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
