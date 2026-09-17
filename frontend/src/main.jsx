import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import App from './App';
import LoadErrorBoundary from './components/layout/LoadErrorBoundary';
import i18n from './locales/i18n';
import { initAnalytics } from './utils/analytics';
import './theme.css';
import './index.css';

initAnalytics();

const application = (
  <React.StrictMode>
    <I18nextProvider i18n={i18n}>
      <BrowserRouter>
        <LoadErrorBoundary>
          <App />
        </LoadErrorBoundary>
      </BrowserRouter>
    </I18nextProvider>
  </React.StrictMode>
);

const root = document.getElementById('root');
if (root.dataset.reactSsr === '1') ReactDOM.hydrateRoot(root, application);
else ReactDOM.createRoot(root).render(application);
