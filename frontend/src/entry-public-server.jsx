import React from 'react';
import { renderToString } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import App from './App';
import i18n from './locales/i18n';

export function renderPublicPage(url, locale, article) {
  i18n.changeLanguage(locale);
  return renderToString(
    <React.StrictMode>
      <I18nextProvider i18n={i18n}>
        <StaticRouter location={url}>
          <App initialArticle={article} />
        </StaticRouter>
      </I18nextProvider>
    </React.StrictMode>,
  );
}
