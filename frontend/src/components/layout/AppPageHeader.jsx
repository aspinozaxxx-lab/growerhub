import React from 'react';
import Stack from '../ui/Stack';
import { Title } from '../ui/Typography';
import './AppPageHeader.css';

function AppPageHeader({ title, description = null, right = null }) {
  return (
    <Stack className="app-page-header" direction="row" gap="3" align="center" justify="space-between" wrap="wrap">
      <div className="app-page-header__copy">
        <Title level={2} className="app-page-header__title">{title}</Title>
        {description ? <p className="app-page-header__description">{description}</p> : null}
      </div>
      {right ? <div className="app-page-header__actions">{right}</div> : null}
    </Stack>
  );
}

export default AppPageHeader;
