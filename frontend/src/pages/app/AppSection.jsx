import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from '../../components/layout/AppLayout';
import RequireAdmin from '../../features/auth/RequireAdmin';
import RequireAuth from '../../features/auth/RequireAuth';
import { SensorStatsProvider } from '../../features/sensors/SensorStatsContext';
import { WateringSidebarProvider } from '../../features/watering/WateringSidebarContext';
import NotFoundPage from '../NotFoundPage';
import LoginPage from './LoginPage';
import { translateApp } from '../../locales/i18n';
import { LEGACY_APP_REDIRECTS } from './appNavigation';

const AppOverview = lazy(() => import('./AppOverview'));
const SensorStatsSidebar = lazy(() => import('../../features/sensors/SensorStatsSidebar'));
const WateringSidebar = lazy(() => import('../../features/watering/WateringSidebar'));
const AppOnboarding = lazy(() => import('./AppOnboarding'));
const AppConnections = lazy(() => import('./AppConnections'));
const AppZigbeeDevices = lazy(() => import('./AppZigbeeDevices'));
const AppAutomations = lazy(() => import('./AppAutomations'));
const AppPlants = lazy(() => import('./AppPlants'));
const AppPlantJournal = lazy(() => import('./AppPlantJournal'));
const AppProfile = lazy(() => import('./AppProfile'));
const AppSettings = lazy(() => import('./AppSettings'));
const FarmConstructor = lazy(() => import('../../features/farm/FarmConstructor'));
const FarmZonesSettings = lazy(() => import('../../features/farm/FarmZonesSettings'));
const AdminAutomation = lazy(() => import('./admin/AdminAutomation'));
const AdminDevices = lazy(() => import('./admin/AdminDevices'));
const AdminFarmDashboard = lazy(() => import('./admin/AdminFarmDashboard'));
const AdminLayout = lazy(() => import('./admin/AdminLayout'));
const AdminManualWatering = lazy(() => import('./admin/AdminManualWatering'));
const AdminMqtt = lazy(() => import('./admin/AdminMqtt'));
const AdminPlants = lazy(() => import('./admin/AdminPlants'));
const AdminProductAnalytics = lazy(() => import('./admin/AdminProductAnalytics'));
const AdminUsers = lazy(() => import('./admin/AdminUsers'));
const AdminZigbee = lazy(() => import('./admin/AdminZigbee'));

function ProtectedAppLayout() {
  return (
    <RequireAuth>
      <WateringSidebarProvider>
        <SensorStatsProvider>
          <AppLayout />
          <SensorStatsSidebar />
          <WateringSidebar />
        </SensorStatsProvider>
      </WateringSidebarProvider>
    </RequireAuth>
  );
}

function AppSection() {
  return (
    <Suspense fallback={<div className="app-loading">{translateApp("Загружаем раздел…")}</div>}>
        <Routes>
          <Route path="login/" element={<LoginPage />} />
          <Route element={<ProtectedAppLayout />}>
            <Route index element={<AppOverview />} />
            <Route path="onboarding/" element={<AppOnboarding />} />
            <Route path="farm/" element={<FarmConstructor />} />
            <Route path="automations/" element={<AppAutomations />} />
            <Route path="plants/" element={<AppPlants />} />
            <Route path="plants/:plantId/journal/" element={<AppPlantJournal />} />
            <Route path="settings/" element={<AppSettings />}>
              <Route index element={<Navigate to="connections/" replace />} />
              <Route path="connections/" element={<AppConnections />} />
              <Route path="zones/" element={<FarmZonesSettings />} />
              <Route path="devices/" element={<AppZigbeeDevices />} />
              <Route path="profile/" element={<AppProfile />} />
            </Route>
            {Object.entries(LEGACY_APP_REDIRECTS).map(([source, target]) => (
              <Route
                key={source}
                path={source.replace(/^\/app\//u, '')}
                element={<Navigate to={target} replace />}
              />
            ))}
            <Route
              path="admin/"
              element={(
                <RequireAdmin>
                  <AdminLayout />
                </RequireAdmin>
              )}
            >
              <Route index element={<Navigate to="dashboard/" replace />} />
              <Route path="dashboard/" element={<AdminFarmDashboard />} />
              <Route path="product-analytics/" element={<AdminProductAnalytics />} />
              <Route path="users/" element={<AdminUsers />} />
              <Route path="devices/" element={<AdminDevices />} />
              <Route path="plants/" element={<AdminPlants />} />
              <Route path="mqtt/" element={<AdminMqtt />} />
              <Route path="zigbee/" element={<AdminZigbee />} />
              <Route path="automation/" element={<AdminAutomation />} />
              <Route path="manual-watering/" element={<AdminManualWatering />} />
            </Route>
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
    </Suspense>
  );
}

export default AppSection;
