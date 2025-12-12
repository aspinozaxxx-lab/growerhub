import React from 'react';
import './SidePanel.css';

function SidePanel({
  isOpen,
  title,
  subtitle,
  children,
  footer,
  onClose,
  side = 'right',
  width = 'md',
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
    <div className={`gh-panel gh-panel--${side} ${isOpen ? 'is-open' : ''}`} role="dialog" aria-modal="true" aria-label={title || closeLabel}>
      <button type="button" className="gh-panel__overlay" onClick={handleOverlayClick} aria-label={closeLabel} />
      <aside className={`gh-panel__drawer gh-panel__drawer--${width}`} onClick={handlePanelClick}>
        <header className="gh-panel__header">
          <div>
            {title ? <div className="gh-panel__title">{title}</div> : null}
            {subtitle ? <div className="gh-panel__subtitle">{subtitle}</div> : null}
          </div>
          <button type="button" className="gh-panel__close" onClick={onClose} aria-label={closeLabel}>
            ×
          </button>
        </header>
        <div className="gh-panel__body">
          {children}
        </div>
        {footer ? <div className="gh-panel__footer">{footer}</div> : null}
      </aside>
    </div>
  );
}

export default SidePanel;
