import React from 'react';
import './AppPageHeader.css';

function AppPageHeader({ title, right = null }) {
  return (
    <div className="app-page-header">
      <h2 className="app-page-header__title">{title}</h2>
      {right ? <div className="app-page-header__actions">{right}</div> : null}
    </div>
  );
}

export default AppPageHeader;
