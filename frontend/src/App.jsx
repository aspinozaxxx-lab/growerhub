import { lazy, Suspense, useEffect, useRef } from 'react';
import { Route, Routes, useLocation } from 'react-router-dom';
import Layout from './components/layout/Layout';
import { METRIKA_ID } from './domain/siteConfig';
import AboutPage from './pages/AboutPage';
import ArticleClusterPage from './pages/ArticleClusterPage';
import ArticlePage from './pages/ArticlePage';
import ArticlesListPage from './pages/ArticlesListPage';
import HomePage from './pages/HomePage';
import EquipmentCategoryPage from './pages/EquipmentCategoryPage';
import EquipmentIndexPage from './pages/EquipmentIndexPage';
import GettingStartedPage from './pages/GettingStartedPage';
import LegalPage from './pages/LegalPage';
import MiniFarmPage from './pages/MiniFarmPage';
import NotFoundPage from './pages/NotFoundPage';
import PumpEarlyAccessPage from './pages/PumpEarlyAccessPage';

const AppSection = lazy(() => import('./pages/app/AppSection'));

function MetrikaRouteTracker() {
  const location = useLocation();
  const previousUrlRef = useRef(document.referrer || '');

  useEffect(() => {
    const url = window.location.href;
    const referer = previousUrlRef.current;
    previousUrlRef.current = url;

    const timerId = window.setTimeout(() => {
      if (typeof window.ym === 'function') {
        window.ym(METRIKA_ID, 'hit', url, {
          referer,
          title: document.title,
        });
      }
    }, 0);

    return () => window.clearTimeout(timerId);
  }, [location.pathname, location.search, location.hash]);

  return null;
}

function App() {
  return (
    <Layout>
      <MetrikaRouteTracker />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/avtomatizatsiya-mini-fermy/" element={<MiniFarmPage />} />
        <Route path="/kak-nachat/" element={<GettingStartedPage />} />
        <Route path="/oborudovanie/" element={<EquipmentIndexPage />} />
        <Route path="/oborudovanie/zigbee-koordinator/" element={<EquipmentCategoryPage categoryKey="coordinators" />} />
        <Route path="/oborudovanie/datchiki/" element={<EquipmentCategoryPage categoryKey="sensors" />} />
        <Route path="/oborudovanie/zigbee-rozetki/" element={<EquipmentCategoryPage categoryKey="sockets" />} />
        <Route path="/oborudovanie/nasos-dlya-poliva/" element={<PumpEarlyAccessPage />} />
        <Route path="/articles/" element={<ArticlesListPage />} />
        <Route path="/articles/clusters/:clusterSlug/" element={<ArticleClusterPage />} />
        <Route path="/articles/:slug/" element={<ArticlePage />} />
        <Route path="/about/" element={<AboutPage />} />
        <Route path="/privacy/" element={<LegalPage type="privacy" />} />
        <Route path="/terms/" element={<LegalPage type="terms" />} />
        <Route
          path="/app/*"
          element={(
            <Suspense fallback={<div className="app-loading">Загружаем приложение…</div>}>
              <AppSection />
            </Suspense>
          )}
        />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Layout>
  );
}

export default App;
