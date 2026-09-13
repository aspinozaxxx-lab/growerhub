import { NavLink, Outlet } from 'react-router-dom';
import AppPageHeader from '../../components/layout/AppPageHeader';
import { translateApp } from '../../locales/i18n';
import { SETTINGS_TABS } from './appNavigation';
import { useAuth } from '../../features/auth/AuthContext';
import './AppSettings.css';

function AppSettings() {
  const { demoActive } = useAuth();
  const tabs = demoActive ? SETTINGS_TABS.filter((tab) => !tab.to.includes("profile")).map((tab) => tab.to.includes("connections") ? { to: "/app/demo-tools/", label: "Демоустройства" } : tab) : SETTINGS_TABS;
  return (
    <div className="app-settings">
      <AppPageHeader title={translateApp("Настройки")} />
      <nav className="app-settings__tabs" aria-label={translateApp("Разделы настроек")}>
        {tabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) => (
              isActive ? 'app-settings__tab is-active' : 'app-settings__tab'
            )}
          >
            {translateApp(tab.label)}
          </NavLink>
        ))}
      </nav>
      <div className="app-settings__content">
        <Outlet />
      </div>
    </div>
  );
}

export default AppSettings;
