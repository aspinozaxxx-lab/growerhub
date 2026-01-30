import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/layout/Layout';
import AppLayout from './components/layout/AppLayout';
import AboutPage from './pages/AboutPage';
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
import RequireAdmin from './features/auth/RequireAdmin';
import AppPlantJournal from './pages/app/AppPlantJournal';
import LoginPage from './pages/app/LoginPage';
import RequireAuth from './features/auth/RequireAuth';
import { SensorStatsProvider } from './features/sensors/SensorStatsContext';
import SensorStatsSidebar from './features/sensors/SensorStatsSidebar';
import { WateringSidebarProvider } from './features/watering/WateringSidebarContext';
import WateringSidebar from './features/watering/WateringSidebar';

function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/articles" element={<ArticlesListPage />} />
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
            <Route index element={<Navigate to="devices" replace />} />
            <Route path="users" element={<AdminUsers />} />
            <Route path="devices" element={<AdminDevices />} />
            <Route path="plants" element={<AdminPlants />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}

export default App;
