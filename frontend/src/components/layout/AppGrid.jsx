import React from 'react';
import './AppGrid.css';

function AppGrid({ children, min = 240 }) {
  return (
    <div className="app-grid" style={{ '--app-grid-min': `${min}px` }}>
      {children}
    </div>
  );
}

export default AppGrid;
