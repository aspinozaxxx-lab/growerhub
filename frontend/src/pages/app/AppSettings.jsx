import { NavLink, Outlet } from 'react-router-dom';
import AppPageHeader from '../../components/layout/AppPageHeader';
import { translateApp } from '../../locales/i18n';
import { SETTINGS_TABS } from './appNavigation';
import './AppSettings.css';

function AppSettings() {
  return (
    <div className="app-settings">
      <AppPageHeader title={translateApp("Настройки")} />
      <nav className="app-settings__tabs" aria-label={translateApp("Разделы настроек")}>
        {SETTINGS_TABS.map((tab) => (
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
