import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Droplets, RefreshCw } from 'lucide-react';
import { fetchFarmsOverview } from '../../api/selfService';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import BoxWateringStatsPanel from '../../features/manual-watering/BoxWateringStatsPanel';
import {
  farmOverviewToDashboardRooms,
  overviewFarms,
  overviewGreenhouses,
} from '../../features/farm/farmModel';
import { FarmDashboardRooms } from '../../features/dashboard/FarmDashboard';
import { formatDateTime } from '../../features/dashboard/dashboardModel';
import { formatTimeHHMM } from '../../utils/formatters';
import { translateApp } from '../../locales/i18n';
import './SelfServicePages.css';

const REFRESH_INTERVAL_MS = 30000;

function AppOverview() {
  const { openSensorStats } = useSensorStatsContext();
  const [overview, setOverview] = useState(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [wateringStatsTarget, setWateringStatsTarget] = useState(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    setError('');
    try {
      const payload = await fetchFarmsOverview();
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

  const farms = overviewFarms(overview);
  const greenhouses = overviewGreenhouses(overview);
  const rooms = useMemo(
    () => farmOverviewToDashboardRooms(overview),
    [overview],
  );
  const updatedLabel = lastUpdatedAt
    ? formatDateTime(lastUpdatedAt.toISOString())
    : translateApp("Ожидает обновления");

  const handleOpenStats = (payload) => {
    if (!payload) return;
    if (payload.mode === 'box-watering') {
      setWateringStatsTarget({ ...payload, title: payload.subtitle || payload.title });
      return;
    }
    openSensorStats({
      ...payload,
      zigbeeHistoryScope: 'self-service',
      equipmentStatsScope: 'self-service',
    });
  };

  if (isLoading && !overview) {
    return <AppPageState kind="loading" title={translateApp("Загружаем ферму…")} />;
  }

  return (
    <div className="self-service-page farm-dashboard">
      <AppPageHeader
        title={translateApp("Обзор")}
        subtitle={translateApp("Состояние всех ферм обновляется каждые 30 секунд")}
        right={(
          <div className="farm-dashboard-header-actions">
            <Link className="farm-dashboard-watering-link" to="/app/manual-watering/">
              <Droplets size={16} aria-hidden="true" />
              <span>{translateApp("Ручной полив")}</span>
            </Link>
            <div className="farm-dashboard-refresh" title={`${translateApp("Обновлено: {{value1}}", { value1: updatedLabel })} · ${translateApp("Состояние всех ферм обновляется каждые 30 секунд")}`}>
              <RefreshCw size={15} aria-hidden="true" />
              <span>{translateApp("Обновлено: {{value1}}", { value1: lastUpdatedAt ? formatTimeHHMM(lastUpdatedAt.toISOString()) : updatedLabel })}</span>
            </div>
          </div>
        )}
      />

      {error ? <AppPageState kind="error" title={error} /> : null}

      {!error && farms.length === 0 ? (
        <AppPageState kind="empty" title={translateApp("Фермы пока не созданы")}>
          <Link className="gh-btn gh-btn--primary gh-btn--md" to="/app/settings/zones/">
            {translateApp("Открыть настройки зон")}
          </Link>
        </AppPageState>
      ) : null}

      {farms.length > 0 ? (
        <>
          {greenhouses.length === 0 ? (
            <AppPageState kind="empty" title={translateApp("Добавьте первую теплицу")}>
              <Link className="gh-btn gh-btn--primary gh-btn--md" to="/app/settings/zones/">
                {translateApp("Открыть настройки зон")}
              </Link>
            </AppPageState>
          ) : (
            <FarmDashboardRooms rooms={rooms} onOpenStats={handleOpenStats} />
          )}
        </>
      ) : null}

      <BoxWateringStatsPanel
        key={wateringStatsTarget?.boxId || 'closed'}
        target={wateringStatsTarget}
        onClose={() => setWateringStatsTarget(null)}
      />
    </div>
  );
}

export default AppOverview;
