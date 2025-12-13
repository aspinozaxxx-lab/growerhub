import React from 'react';
import Stack from '../ui/Stack';
import { Title } from '../ui/Typography';
import './AppPageHeader.css';

function AppPageHeader({ title, right = null }) {
  return (
    <Stack className="app-page-header" direction="row" gap="3" align="center" justify="space-between" wrap="wrap">
      <Title level={2} className="app-page-header__title">{title}</Title>
      {right ? <div className="app-page-header__actions">{right}</div> : null}
    </Stack>
  );
}

export default AppPageHeader;
