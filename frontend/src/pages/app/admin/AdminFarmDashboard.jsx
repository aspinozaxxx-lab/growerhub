import React, { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  CircleDot,
  Droplets,
  Fan,
  Gauge,
  Leaf,
  Lightbulb,
  RefreshCw,
  Snowflake,
  Sprout,
  Thermometer,
  Wind,
} from 'lucide-react';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import Surface from '../../../components/ui/Surface';
import { useAuth } from '../../../features/auth/AuthContext';
import { useSensorStatsContext } from '../../../features/sensors/SensorStatsContext';
import { isSessionExpiredError } from '../../../api/client';
import { fetchAdminAutomationOverview } from '../../../api/admin';
import BoxWateringStatsPanel from '../../../features/manual-watering/BoxWateringStatsPanel';
import {
  RESOURCE_ROLES,
  SCENARIO_TYPES,
  buildAcRequestBoxes,
  buildResourceStatsPayload,
  countPlantsInRoom,
  findResource,
  findScenario,
  findState,
  formatDateTime,
  formatResourceValue,
  hasCurrentValue,
  isEquipmentActive,
  listOrEmpty,
  resourceReadyLabel,
  resourceRoleLabel,
  resourceTone,
  scenarioDisplayStatus,
  scenarioTone,
  scenarioTypeLabel,
} from './adminFarmDashboardModel';
import './AdminFarmDashboard.css';

const REFRESH_INTERVAL_MS = 30000;
const BOX_SCENARIOS = [
  SCENARIO_TYPES.BOX_CLIMATE,
  SCENARIO_TYPES.LIGHT_SCHEDULE,
  SCENARIO_TYPES.WATERING,
];
const BOX_EQUIPMENT_ROLES = [
  RESOURCE_ROLES.EXHAUST_SWITCH,
  RESOURCE_ROLES.LIGHT_SWITCH,
  RESOURCE_ROLES.WATER_PUMP,
];

function StatusBadge({ children, tone = 'muted', icon: Icon = CircleDot }) {
  return (
    <span className={`farm-dashboard-badge farm-dashboard-badge--${tone}`}>
      {React.createElement(Icon, { size: 14, 'aria-hidden': true })}
      <span>{children}</span>
    </span>
  );
}

function ResourceTile({ role, resource, icon: Icon, motion = 'pulse', statsSubtitle, statsScope, onOpenStats }) {
  const active = isEquipmentActive(resource, role);
  const tone = resourceTone(resource, active);
  const value = formatResourceValue(resource, role);
  const hasValue = hasCurrentValue(resource);
  const statsPayload = buildResourceStatsPayload(resource, role, statsSubtitle, statsScope);

  const classes = [
    'farm-dashboard-resource',
    `farm-dashboard-resource--${tone}`,
    active ? 'is-active' : '',
    !hasValue && resource ? 'is-empty-value' : '',
    statsPayload ? 'is-clickable' : '',
  ].filter(Boolean).join(' ');
  const content = (
    <>
      <div className={`farm-dashboard-resource__icon ${active ? `is-${motion}` : ''}`} aria-hidden="true">
        {React.createElement(Icon, { size: 26 })}
      </div>
      <div className="farm-dashboard-resource__body">
        <div className="farm-dashboard-resource__label">{resourceRoleLabel(role)}</div>
        <div className="farm-dashboard-resource__value">{value}</div>
      </div>
      <div className="farm-dashboard-resource__footer">
        <span>{resourceReadyLabel(resource)}</span>
      </div>
    </>
  );

  if (statsPayload) {
    return (
      <button
        type="button"
        className={classes}
        onClick={() => onOpenStats?.(statsPayload)}
        aria-label={`Открыть статистику: ${resourceRoleLabel(role)}`}
      >
        {content}
      </button>
    );
  }

  return (
    <div className={classes}>
      {content}
    </div>
  );
}

function SensorTile({ resource, statsSubtitle, onOpenStats }) {
  const role = resource?.role;
  const Icon = role === RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR ? Thermometer : Gauge;
  const tone = resourceTone(resource, false);
  const value = formatResourceValue(resource, role);

  const statsPayload = buildResourceStatsPayload(resource, role, statsSubtitle);
  const classes = [
    'farm-dashboard-sensor',
    `farm-dashboard-sensor--${tone}`,
    statsPayload ? 'is-clickable' : '',
  ].filter(Boolean).join(' ');
  const content = (
    <>
      <Icon size={20} aria-hidden="true" />
      <div>
        <div className="farm-dashboard-sensor__label">{resourceRoleLabel(role)}</div>
        <div className="farm-dashboard-sensor__value">{value}</div>
      </div>
      <strong>{resourceReadyLabel(resource)}</strong>
    </>
  );

  if (statsPayload) {
    return (
      <button
        type="button"
        className={classes}
        onClick={() => onOpenStats?.(statsPayload)}
        aria-label={`Открыть статистику: ${resourceRoleLabel(role)}`}
      >
        {content}
      </button>
    );
  }

  return (
    <div className={classes}>
      {content}
    </div>
  );
}

function ScenarioPill({ scenarioType, scenarios, states }) {
  const scenario = findScenario(scenarios, scenarioType);
  const state = findState(states, scenarioType);
  const tone = scenarioTone(state, scenario);
  return (
    <StatusBadge tone={tone} icon={Activity}>
      {scenarioTypeLabel(scenarioType)}: {scenarioDisplayStatus(state, scenario)}
    </StatusBadge>
  );
}

function AcRequestList({ boxes }) {
  if (boxes.length === 0) {
    return (
      <div className="farm-dashboard-ac-requests__empty">
        <CircleDot size={16} aria-hidden="true" />
        <span>Запросов нет</span>
      </div>
    );
  }

  return (
    <div className="farm-dashboard-ac-requests__list">
      {boxes.map((box) => (
        <span key={box.id || box.name} className="farm-dashboard-request-chip">
          <AlertTriangle size={14} aria-hidden="true" />
          <span>{box.name || 'Бокс без названия'}</span>
        </span>
      ))}
    </div>
  );
}

function PlantList({ plants }) {
  const items = listOrEmpty(plants);
  if (items.length === 0) {
    return <span className="farm-dashboard-plants__empty">Растения не привязаны</span>;
  }

  return (
    <div className="farm-dashboard-plants__chips">
      {items.map((plant) => (
        <span key={plant.id || plant.name}>{plant.name || 'Растение без названия'}</span>
      ))}
    </div>
  );
}

function FarmBox({ box, onOpenStats }) {
  const resources = listOrEmpty(box.resources);
  const sensors = resources.filter((resource) => [
    RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR,
    RESOURCE_ROLES.SOIL_MOISTURE_SENSOR,
  ].includes(resource?.role));
  const plants = listOrEmpty(box.plants);
  const statsSubtitle = box.name || 'Бокс без названия';

  return (
    <section className={`farm-dashboard-box ${box.enabled ? '' : 'is-disabled'}`}>
      <header className="farm-dashboard-box__header">
        <div>
          <h4>{box.name || 'Бокс без названия'}</h4>
          <span>{plants.length} растений</span>
        </div>
        <StatusBadge tone={box.enabled ? 'success' : 'muted'}>
          {box.enabled ? 'Активен' : 'Выключен'}
        </StatusBadge>
      </header>

      <div className="farm-dashboard-box__scene">
        <div className="farm-dashboard-box__plants">
          <Sprout size={28} aria-hidden="true" />
          <PlantList plants={plants} />
        </div>
        <div className="farm-dashboard-box__equipment">
          {BOX_EQUIPMENT_ROLES.map((role) => (
            <ResourceTile
              key={role}
              role={role}
              resource={findResource(resources, role)}
              icon={role === RESOURCE_ROLES.EXHAUST_SWITCH ? Fan : role === RESOURCE_ROLES.LIGHT_SWITCH ? Lightbulb : Droplets}
              motion={role === RESOURCE_ROLES.EXHAUST_SWITCH ? 'spin' : role === RESOURCE_ROLES.LIGHT_SWITCH ? 'glow' : 'water'}
              statsSubtitle={statsSubtitle}
              statsScope={{ boxId: box.id }}
              onOpenStats={onOpenStats}
            />
          ))}
        </div>
      </div>

      <div className="farm-dashboard-sensors">
        {sensors.length === 0 ? (
          <div className="farm-dashboard-empty-line">Датчики не привязаны</div>
        ) : sensors.map((sensor) => (
          <SensorTile
            key={sensor.id || sensor.role}
            resource={sensor}
            statsSubtitle={statsSubtitle}
            onOpenStats={onOpenStats}
          />
        ))}
      </div>

      <div className="farm-dashboard-scenarios">
        {BOX_SCENARIOS.map((scenarioType) => (
          <ScenarioPill
            key={scenarioType}
            scenarioType={scenarioType}
            scenarios={box.scenarios}
            states={box.states}
          />
        ))}
      </div>
    </section>
  );
}

function FarmRoom({ room, onOpenStats }) {
  const boxes = listOrEmpty(room.boxes);
  const acResource = findResource(room.resources, RESOURCE_ROLES.AC_SWITCH);
  const acRequests = buildAcRequestBoxes(room);
  const roomScenario = findScenario(room.scenarios, SCENARIO_TYPES.ROOM_CLIMATE);
  const roomState = findState(room.states, SCENARIO_TYPES.ROOM_CLIMATE);
  const plantCount = countPlantsInRoom(room);

  return (
    <Surface variant="card" padding="md" className="farm-dashboard-room">
      <header className="farm-dashboard-room__header">
        <div>
          <h3>{room.name || 'Ферма без названия'}</h3>
          <span>Общие ресурсы фермы</span>
        </div>
        <div className="farm-dashboard-room__badges">
          <StatusBadge tone={room.enabled ? 'success' : 'muted'}>
            {room.enabled ? 'Активна' : 'Выключена'}
          </StatusBadge>
          <ScenarioPill
            scenarioType={SCENARIO_TYPES.ROOM_CLIMATE}
            scenarios={room.scenarios}
            states={room.states}
          />
        </div>
      </header>

      <div className="farm-dashboard-room__summary">
        <div className="farm-dashboard-summary-tile">
          <Wind size={20} aria-hidden="true" />
          <span>Боксы</span>
          <strong>{boxes.length}</strong>
        </div>
        <div className="farm-dashboard-summary-tile">
          <Leaf size={20} aria-hidden="true" />
          <span>Растения</span>
          <strong>{plantCount}</strong>
        </div>
        <div className="farm-dashboard-summary-tile">
          <Activity size={20} aria-hidden="true" />
          <span>Запросы кондиционера</span>
          <strong>{acRequests.length}</strong>
        </div>
      </div>

      <div className="farm-dashboard-room__climate">
        <ResourceTile
          role={RESOURCE_ROLES.AC_SWITCH}
          resource={acResource}
          icon={Snowflake}
          motion="cool"
          statsSubtitle={room.name || 'Ферма без названия'}
          onOpenStats={onOpenStats}
        />
        <div className="farm-dashboard-ac-requests">
          <div>
            <h4>Запросы на кондиционер</h4>
            <span>{scenarioTypeLabel(SCENARIO_TYPES.ROOM_CLIMATE)}: {scenarioDisplayStatus(roomState, roomScenario)}</span>
          </div>
          <AcRequestList boxes={acRequests} />
        </div>
      </div>

      <div className="farm-dashboard-boxes">
        {boxes.length === 0 ? (
          <div className="farm-dashboard-empty-line">Боксы не настроены</div>
        ) : boxes.map((box) => (
          <FarmBox key={box.id} box={box} onOpenStats={onOpenStats} />
        ))}
      </div>
    </Surface>
  );
}

function AdminFarmDashboard() {
  const { token } = useAuth();
  const { openSensorStats } = useSensorStatsContext();
  const [overview, setOverview] = useState(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [wateringStatsTarget, setWateringStatsTarget] = useState(null);

  useEffect(() => {
    let isCancelled = false;

    async function loadOverview(silent = false) {
      if (!silent) {
        setIsLoading(true);
      }
      setError('');
      try {
        const data = await fetchAdminAutomationOverview(token);
        if (isCancelled) return;
        setOverview(data && typeof data === 'object' ? data : { rooms: [] });
        setLastUpdatedAt(new Date());
      } catch (err) {
        if (isCancelled || isSessionExpiredError(err)) return;
        setError(err?.message || 'Не удалось загрузить дашборд фермы');
      } finally {
        if (!isCancelled && !silent) {
          setIsLoading(false);
        }
      }
    }

    loadOverview(false);

    const intervalId = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        loadOverview(true);
      }
    }, REFRESH_INTERVAL_MS);
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        loadOverview(true);
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      isCancelled = true;
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [token]);

  const rooms = useMemo(() => listOrEmpty(overview?.rooms), [overview]);
  const updatedLabel = lastUpdatedAt ? formatDateTime(lastUpdatedAt.toISOString()) : 'Ожидает обновления';
  const handleOpenStats = (payload) => {
    if (payload?.mode === 'box-watering') {
      setWateringStatsTarget({ ...payload, title: payload.subtitle || payload.title });
      return;
    }
    openSensorStats(payload);
  };

  return (
    <div className="admin-page farm-dashboard">
      <AppPageHeader
        title="Дашборд фермы"
        subtitle="Текущее состояние боксов и общих ресурсов"
        right={(
          <div className="farm-dashboard-refresh">
            <RefreshCw size={15} aria-hidden="true" />
            <span>Обновлено: {updatedLabel}</span>
          </div>
        )}
      />

      {isLoading && !overview && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <AppPageState kind="error" title={error} />}

      {!isLoading && !error && rooms.length === 0 && (
        <AppPageState
          kind="empty"
          title="Ферма пока не настроена"
          hint="Создайте помещение, боксы и привязки в разделе автоматизации."
        />
      )}

      {rooms.length > 0 && (
        <div className="farm-dashboard-rooms">
          {rooms.map((room) => (
            <FarmRoom key={room.id} room={room} onOpenStats={handleOpenStats} />
          ))}
        </div>
      )}

      <BoxWateringStatsPanel
        key={wateringStatsTarget?.boxId || 'closed'}
        target={wateringStatsTarget}
        onClose={() => setWateringStatsTarget(null)}
      />
    </div>
  );
}

export default AdminFarmDashboard;
