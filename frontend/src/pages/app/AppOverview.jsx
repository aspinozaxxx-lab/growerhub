import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Boxes, Cpu, Droplets, Leaf, RefreshCw } from 'lucide-react';
import { fetchFarmsOverview } from '../../api/selfService';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import BoxWateringStatsPanel from '../../features/manual-watering/BoxWateringStatsPanel';
import {
  farmOverviewToDashboardRooms,
  findUnassignedFarmPlants,
  listFarmWarnings,
  listOrEmpty,
  overviewFarms,
  overviewGreenhouses,
} from '../../features/farm/farmModel';
import { FarmDashboardRooms } from '../../features/dashboard/FarmDashboard';
import { formatDateTime } from '../../features/dashboard/dashboardModel';
import { translateApp } from '../../locales/i18n';
import './SelfServicePages.css';

const REFRESH_INTERVAL_MS = 30000;

function AppOverview() {
  const { openSensorStats } = useSensorStatsContext();
  const [overview, setOverview] = useState(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [warningTooltipOpen, setWarningTooltipOpen] = useState(false);
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
  const catalog = overview?.resource_catalog || {};
  const plants = listOrEmpty(catalog.plants);
  const unassignedPlants = findUnassignedFarmPlants(overview);
  const plantCount = plants.length;
  const deviceCount = listOrEmpty(catalog.native_devices).length
    + listOrEmpty(catalog.zigbee_devices).length;
  const warnings = listFarmWarnings(overview);
  const warningCount = warnings.length;
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
            <div className="farm-dashboard-refresh">
              <RefreshCw size={15} aria-hidden="true" />
              <span>{translateApp("Обновлено: {{value1}}", { value1: updatedLabel })}</span>
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
          <div className="summary-grid farm-overview-summary">
            <article>
              <Boxes size={20} aria-hidden="true" />
              <span>{translateApp("Теплицы")}</span>
              <strong>{greenhouses.length}</strong>
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
            <article
              className={[
                warningCount > 0 ? 'has-warning has-tooltip' : '',
                warningTooltipOpen ? 'is-tooltip-open' : '',
              ].filter(Boolean).join(' ')}
              tabIndex={warningCount > 0 ? 0 : undefined}
              aria-describedby={warningCount > 0 ? 'farm-warning-tooltip' : undefined}
              aria-expanded={warningCount > 0 ? warningTooltipOpen : undefined}
              role={warningCount > 0 ? 'button' : undefined}
              onClick={warningCount > 0
                ? () => setWarningTooltipOpen((current) => !current)
                : undefined}
              onKeyDown={warningCount > 0 ? (event) => {
                if (event.key === 'Escape') {
                  setWarningTooltipOpen(false);
                } else if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  setWarningTooltipOpen((current) => !current);
                }
              } : undefined}
              onBlur={warningCount > 0 ? (event) => {
                if (!event.currentTarget.contains(event.relatedTarget)) {
                  setWarningTooltipOpen(false);
                }
              } : undefined}
            >
              <AlertTriangle size={20} aria-hidden="true" />
              <span>{translateApp("Предупреждения")}</span>
              <strong>{warningCount}</strong>
              {warningCount > 0 ? (
                <div
                  id="farm-warning-tooltip"
                  className="farm-warning-tooltip"
                  role="tooltip"
                >
                  <div className="farm-warning-tooltip__title">
                    {translateApp('Предупреждения: {{value1}}', { value1: warningCount })}
                  </div>
                  <ul>
                    {warnings.map((warning) => (
                      <li key={warning.id}>
                        <span>
                          {translateApp(warning.scopeLabel)}
                          {' «'}
                          {warning.scopeName || translateApp('Без названия')}
                          {'»'}
                        </span>
                        <span>
                          {translateApp(warning.label)}
                          {' — '}
                          {translateApp(warning.message)}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}
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
              <Link className="gh-btn gh-btn--secondary gh-btn--md" to="/app/plants/">
                {translateApp("Открыть растения")}
              </Link>
            </section>
          ) : null}

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
