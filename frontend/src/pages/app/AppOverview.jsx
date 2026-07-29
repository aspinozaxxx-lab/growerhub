import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Boxes, Cpu, Leaf, RefreshCw } from 'lucide-react';
import { fetchFarmOverview } from '../../api/selfService';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import {
  countFarmWarnings,
  farmOverviewToDashboardRooms,
  findUnassignedFarmPlants,
  listOrEmpty,
} from '../../features/farm/farmModel';
import { FarmDashboardRooms } from './admin/AdminFarmDashboard';
import { formatDateTime } from './admin/adminFarmDashboardModel';
import { translateApp } from '../../locales/i18n';
import './SelfServicePages.css';

const REFRESH_INTERVAL_MS = 30000;

function AppOverview() {
  const { openSensorStats } = useSensorStatsContext();
  const [overview, setOverview] = useState(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    setError('');
    try {
      const payload = await fetchFarmOverview();
      setOverview(payload && typeof payload === 'object' ? payload : {});
      setLastUpdatedAt(new Date());
    } catch (requestError) {
      setError(requestError?.message || translateApp("Не удалось загрузить обзор фермы"));
    } finally {
      if (!silent) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load(false);
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === 'visible') load(true);
    }, REFRESH_INTERVAL_MS);
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') load(true);
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [load]);

  const zones = listOrEmpty(overview?.farm?.zones);
  const rooms = useMemo(
    () => farmOverviewToDashboardRooms(overview, translateApp("Контур теплицы")),
    [overview],
  );
  const catalog = overview?.resource_catalog || {};
  const plants = listOrEmpty(catalog.plants);
  const unassignedPlants = findUnassignedFarmPlants(overview);
  const plantCount = plants.length;
  const deviceCount = listOrEmpty(catalog.native_devices).length
    + listOrEmpty(catalog.zigbee_devices).length;
  const warningCount = countFarmWarnings(overview);
  const updatedLabel = lastUpdatedAt
    ? formatDateTime(lastUpdatedAt.toISOString())
    : translateApp("Ожидает обновления");

  const handleOpenStats = (payload) => {
    if (!payload || payload.mode === 'box-watering') return;
    openSensorStats(payload);
  };

  if (isLoading && !overview) {
    return <AppPageState kind="loading" title={translateApp("Загружаем ферму…")} />;
  }

  return (
    <div className="self-service-page farm-dashboard">
      <AppPageHeader
        title={translateApp("Обзор")}
        subtitle={translateApp("Состояние всей фермы обновляется каждые 30 секунд")}
        right={(
          <div className="farm-dashboard-refresh">
            <RefreshCw size={15} aria-hidden="true" />
            <span>{translateApp("Обновлено: {{value1}}", { value1: updatedLabel })}</span>
          </div>
        )}
      />

      {error ? <AppPageState kind="error" title={error} /> : null}

      {!error && !overview?.farm ? (
        <AppPageState kind="empty" title={translateApp("Ферма пока не создана")}>
          <Link className="gh-btn gh-btn--primary gh-btn--md" to="/app/farm/">
            {translateApp("Открыть Конструктор фермы")}
          </Link>
        </AppPageState>
      ) : null}

      {overview?.farm ? (
        <>
          <div className="summary-grid farm-overview-summary">
            <article>
              <Boxes size={20} aria-hidden="true" />
              <span>{translateApp("Теплицы")}</span>
              <strong>{zones.length}</strong>
            </article>
            <article>
              <Leaf size={20} aria-hidden="true" />
              <span>{translateApp("Растения")}</span>
              <strong>{plantCount}</strong>
            </article>
            <article>
              <Cpu size={20} aria-hidden="true" />
              <span>{translateApp("Устройства")}</span>
              <strong>{deviceCount}</strong>
            </article>
            <article className={warningCount > 0 ? 'has-warning' : ''}>
              <AlertTriangle size={20} aria-hidden="true" />
              <span>{translateApp("Предупреждения")}</span>
              <strong>{warningCount}</strong>
            </article>
          </div>

          {unassignedPlants.length > 0 ? (
            <section className="farm-overview-unassigned">
              <div>
                <AlertTriangle size={20} aria-hidden="true" />
                <div>
                  <h3>{translateApp("Растения без теплицы")}</h3>
                  <p>{translateApp("Разместите растения, чтобы включить для них автоматизацию.")}</p>
                </div>
              </div>
              <div className="farm-overview-unassigned__plants">
                {unassignedPlants.map((plant) => (
                  <span key={plant.id}>{plant.name || translateApp("Растение без названия")}</span>
                ))}
              </div>
              <Link className="gh-btn gh-btn--secondary gh-btn--md" to="/app/farm/">
                {translateApp("Разместить в конструкторе")}
              </Link>
            </section>
          ) : null}

          {zones.length === 0 ? (
            <AppPageState kind="empty" title={translateApp("Добавьте первую теплицу")}>
              <Link className="gh-btn gh-btn--primary gh-btn--md" to="/app/farm/">
                {translateApp("Открыть конструктор")}
              </Link>
            </AppPageState>
          ) : (
            <FarmDashboardRooms rooms={rooms} zoneView onOpenStats={handleOpenStats} />
          )}
        </>
      ) : null}
    </div>
  );
}

export default AppOverview;
