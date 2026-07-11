import { Navigate, Route, Routes } from 'react-router-dom';
import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import Layout from './components/layout/Layout';
import AppLayout from './components/layout/AppLayout';
import AboutPage from './pages/AboutPage';
import ArticleClusterPage from './pages/ArticleClusterPage';
import ArticlePage from './pages/ArticlePage';
import ArticlesListPage from './pages/ArticlesListPage';
import HomePage from './pages/HomePage';
import AppDashboard from './pages/app/AppDashboard';
import AppDevices from './pages/app/AppDevices';
import AppPlants from './pages/app/AppPlants';
import AppProfile from './pages/app/AppProfile';
import AdminLayout from './pages/app/admin/AdminLayout';
import AdminUsers from './pages/app/admin/AdminUsers';
import AdminDevices from './pages/app/admin/AdminDevices';
import AdminPlants from './pages/app/admin/AdminPlants';
import AdminMqtt from './pages/app/admin/AdminMqtt';
import AdminZigbee from './pages/app/admin/AdminZigbee';
import AdminAutomation from './pages/app/admin/AdminAutomation';
import AdminFarmDashboard from './pages/app/admin/AdminFarmDashboard';
import AdminManualWatering from './pages/app/admin/AdminManualWatering';
import RequireAdmin from './features/auth/RequireAdmin';
import AppPlantJournal from './pages/app/AppPlantJournal';
import LoginPage from './pages/app/LoginPage';
import RequireAuth from './features/auth/RequireAuth';
import { SensorStatsProvider } from './features/sensors/SensorStatsContext';
import SensorStatsSidebar from './features/sensors/SensorStatsSidebar';
import { WateringSidebarProvider } from './features/watering/WateringSidebarContext';
import WateringSidebar from './features/watering/WateringSidebar';

const YANDEX_METRIKA_ID = 110256357;

function MetrikaRouteTracker() {
  const location = useLocation();
  const previousUrlRef = useRef(document.referrer || '');

  useEffect(() => {
    const url = window.location.href;
    const referer = previousUrlRef.current;
    previousUrlRef.current = url;

    const timerId = window.setTimeout(() => {
      if (typeof window.ym === 'function') {
        window.ym(YANDEX_METRIKA_ID, 'hit', url, {
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
        <Route path="/articles" element={<ArticlesListPage />} />
        <Route path="/articles/clusters/:clusterSlug" element={<ArticleClusterPage />} />
        <Route path="/articles/:slug" element={<ArticlePage />} />
        <Route path="/about" element={<AboutPage />} />
        <Route path="/app/login" element={<LoginPage />} />
        <Route
          path="/app"
          element={(
            <RequireAuth>
              <WateringSidebarProvider>
                <SensorStatsProvider>
                  <>
                    <AppLayout />
                    <SensorStatsSidebar />
                    <WateringSidebar />
                  </>
                </SensorStatsProvider>
              </WateringSidebarProvider>
            </RequireAuth>
          )}
        >
          <Route index element={<AppDashboard />} />
          <Route path="plants" element={<AppPlants />} />
          <Route path="plants/:plantId/journal" element={<AppPlantJournal />} />
          <Route path="devices" element={<AppDevices />} />
          <Route path="profile" element={<AppProfile />} />
          <Route
            path="admin"
            element={(
              <RequireAdmin>
                <AdminLayout />
              </RequireAdmin>
            )}
          >
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<AdminFarmDashboard />} />
            <Route path="users" element={<AdminUsers />} />
            <Route path="devices" element={<AdminDevices />} />
            <Route path="plants" element={<AdminPlants />} />
            <Route path="mqtt" element={<AdminMqtt />} />
            <Route path="zigbee" element={<AdminZigbee />} />
            <Route path="automation" element={<AdminAutomation />} />
            <Route path="manual-watering" element={<AdminManualWatering />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}

export default App;
