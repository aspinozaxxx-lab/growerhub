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
          <Route path="devices" element={<AppDevices />} />
          <Route path="profile" element={<AppProfile />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}

export default App;
