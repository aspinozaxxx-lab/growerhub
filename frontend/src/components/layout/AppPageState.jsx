import React from 'react';
import './AppPageState.css';

function AppPageState({ kind = 'empty', title = '', hint = '', children = null }) {
  const className = `app-page-state app-page-state--${kind}`;

  return (
    <div className={className}>
      <div className="app-page-state__content">
        {title ? <div className="app-page-state__title">{title}</div> : null}
        {hint ? <div className="app-page-state__hint">{hint}</div> : null}
        {children}
      </div>
    </div>
  );
}

export default AppPageState;
