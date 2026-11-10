import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import App from './App';
import LoadErrorBoundary from './components/layout/LoadErrorBoundary';
import { findStaticRoute } from './domain/localizedRoutes';
import i18n from './locales/i18n';
import { initAnalytics } from './utils/analytics';
import './theme.css';
import './index.css';

initAnalytics();

const root = document.getElementById('root');
const hydrate = root.dataset.reactSsr === '1';

const mount = (initialNewsPage) => {
  const application = (
    <React.StrictMode>
      <I18nextProvider i18n={i18n}>
        <BrowserRouter>
          <LoadErrorBoundary>
            <App initialNewsPage={initialNewsPage} />
          </LoadErrorBoundary>
        </BrowserRouter>
      </I18nextProvider>
    </React.StrictMode>
  );

  if (hydrate) ReactDOM.hydrateRoot(root, application);
  else ReactDOM.createRoot(root).render(application);
};

if (hydrate && findStaticRoute(window.location.pathname)?.routeId === 'news') {
  import('./pages/NewsPage').then(
    ({ default: NewsPage }) => mount(React.createElement(NewsPage)),
    () => mount(),
  );
} else {
  mount();
}
