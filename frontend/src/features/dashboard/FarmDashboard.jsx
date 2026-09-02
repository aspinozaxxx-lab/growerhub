import React from 'react';
import {
  Activity,
  AlertTriangle,
  CircleDot,
  Droplets,
  Fan,
  Gauge,
  Lightbulb,
  Snowflake,
  Thermometer,
  Wind,
} from 'lucide-react';
import Surface from '../../components/ui/Surface';
import { translateApp } from '../../locales/i18n';
import {
  RESOURCE_ROLES,
  SCENARIO_TYPES,
  acControlStatusLabel,
  buildAcRequestBoxes,
  buildResourceStatsPayload,
  findResource,
  findScenario,
  findState,
  formatResourceValue,
  hasCurrentValue,
  isEquipmentActive,
  listOrEmpty,
  resourceLastSeenLabel,
  resourceReadyLabel,
  resourceRoleLabel,
  resourceTone,
  scenarioDisplayStatus,
  scenarioTone,
  scenarioTypeLabel,
} from './dashboardModel';
import './FarmDashboard.css';
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
        aria-label={translateApp("Открыть статистику: {{value1}}", { value1: resourceRoleLabel(role) })}
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
  const Icon = role === RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR
    ? Thermometer
    : role === RESOURCE_ROLES.AIR_HUMIDITY_SENSOR
      ? Droplets
      : role === RESOURCE_ROLES.LEAK_SENSOR
        ? AlertTriangle
        : Gauge;
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
      <strong>{resourceLastSeenLabel(resource)}</strong>
    </>
  );

  if (statsPayload) {
    return (
      <button
        type="button"
        className={classes}
        onClick={() => onOpenStats?.(statsPayload)}
        aria-label={translateApp("Открыть статистику: {{value1}}", { value1: resourceRoleLabel(role) })}
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
        <span>{translateApp("Запросов нет")}</span>
      </div>
    );
  }

  return (
    <div className="farm-dashboard-ac-requests__list">
      {boxes.map((box) => (
        <span key={box.id || box.name} className="farm-dashboard-request-chip">
          <AlertTriangle size={14} aria-hidden="true" />
          <span>{box.name || translateApp("Теплица без названия")}</span>
        </span>
      ))}
    </div>
  );
}

function FarmBox({
  box,
  hideHeader = false,
  statsSubtitleOverride = '',
  onOpenStats,
}) {
  const resources = listOrEmpty(box.resources);
  const sensors = resources.filter((resource) => [
    RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR,
    RESOURCE_ROLES.AIR_HUMIDITY_SENSOR,
    RESOURCE_ROLES.LEAK_SENSOR,
    RESOURCE_ROLES.SOIL_MOISTURE_SENSOR,
  ].includes(resource?.role));
  const equipment = BOX_EQUIPMENT_ROLES
    .map((role) => ({ role, resource: findResource(resources, role) }))
    .filter(({ resource }) => Boolean(resource));
  const statsSubtitle = statsSubtitleOverride || box.name || translateApp("Теплица без названия");

  return (
    <section className={`farm-dashboard-box ${hideHeader ? 'is-zone-content' : ''} ${box.enabled ? '' : 'is-disabled'}`}>
      {!hideHeader ? (
        <header className="farm-dashboard-box__header">
          <div>
            <h4>{box.name || translateApp("Теплица без названия")}</h4>
          </div>
          {!box.enabled ? (
            <StatusBadge tone="muted">{translateApp("Выключен")}</StatusBadge>
          ) : null}
        </header>
      ) : null}

      {equipment.length > 0 ? (
        <div className="farm-dashboard-box__equipment">
          {equipment.map(({ role, resource }) => (
            <ResourceTile
              key={role}
              role={role}
              resource={resource}
              icon={role === RESOURCE_ROLES.EXHAUST_SWITCH
                ? Fan
                : role === RESOURCE_ROLES.LIGHT_SWITCH
                  ? Lightbulb
                  : Droplets}
              motion={role === RESOURCE_ROLES.EXHAUST_SWITCH
                ? 'spin'
                : role === RESOURCE_ROLES.LIGHT_SWITCH
                  ? 'glow'
                  : 'water'}
              statsSubtitle={statsSubtitle}
              statsScope={{ boxId: box.id }}
              onOpenStats={onOpenStats}
            />
          ))}
        </div>
      ) : null}

      {sensors.length > 0 ? (
        <div className="farm-dashboard-sensors">
          {sensors.map((sensor) => (
            <SensorTile
              key={sensor.id || sensor.role}
              resource={sensor}
              statsSubtitle={statsSubtitle}
              onOpenStats={onOpenStats}
            />
          ))}
        </div>
      ) : null}

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

function FarmRoom({ room, zoneView = false, onOpenStats }) {
  const boxes = listOrEmpty(room.boxes);
  const acResource = findResource(room.resources, RESOURCE_ROLES.AC_SWITCH);
  const acRequests = buildAcRequestBoxes(room);
  const roomScenario = findScenario(room.scenarios, SCENARIO_TYPES.ROOM_CLIMATE);
  const roomState = findState(room.states, SCENARIO_TYPES.ROOM_CLIMATE);

  return (
    <Surface variant="card" padding="md" className="farm-dashboard-room">
      <header className="farm-dashboard-room__header">
        <div>
          <h3>{room.name || translateApp("Ферма без названия")}</h3>
          <span>{zoneView ? translateApp("Ресурсы теплицы") : translateApp("Общие ресурсы фермы")}</span>
        </div>
        <div className="farm-dashboard-room__badges">
          <StatusBadge tone={room.enabled ? 'success' : 'muted'}>
            {room.enabled ? translateApp("Активна") : translateApp("Выключена")}
          </StatusBadge>
          <ScenarioPill
            scenarioType={SCENARIO_TYPES.ROOM_CLIMATE}
            scenarios={room.scenarios}
            states={room.states}
          />
        </div>
      </header>

      <div className={`farm-dashboard-room__summary ${zoneView ? 'is-zone-view' : ''}`}>
        {!zoneView ? (
          <div className="farm-dashboard-summary-tile">
            <Wind size={20} aria-hidden="true" />
            <span>{translateApp("Теплицы")}</span>
            <strong>{boxes.length}</strong>
          </div>
        ) : null}
        <div className="farm-dashboard-summary-tile">
          <Activity size={20} aria-hidden="true" />
          <span>{translateApp("Запросы кондиционера")}</span>
          <strong>{acRequests.length}</strong>
        </div>
      </div>

      <div className="farm-dashboard-room__climate">
        <ResourceTile
          role={RESOURCE_ROLES.AC_SWITCH}
          resource={acResource}
          icon={Snowflake}
          motion="cool"
          statsSubtitle={room.name || translateApp("Ферма без названия")}
          onOpenStats={onOpenStats}
        />
        <div className="farm-dashboard-ac-requests">
          <div>
            <h4>{translateApp("Запросы на кондиционер")}</h4>
            <span>{scenarioTypeLabel(SCENARIO_TYPES.ROOM_CLIMATE)}: {scenarioDisplayStatus(roomState, roomScenario)}</span>
            {acControlStatusLabel(roomState) ? (
              <small>{acControlStatusLabel(roomState)}</small>
            ) : null}
          </div>
          <AcRequestList boxes={acRequests} />
        </div>
      </div>

      <div className="farm-dashboard-boxes">
        {boxes.length === 0 ? (
          <div className="farm-dashboard-empty-line">{translateApp("Теплицы не настроены")}</div>
        ) : boxes.map((box) => (
          <FarmBox
            key={box.id}
            box={box}
            hideHeader={zoneView}
            statsSubtitleOverride={zoneView ? room.name : ''}
            onOpenStats={onOpenStats}
          />
        ))}
      </div>
    </Surface>
  );
}

export function FarmDashboardRooms({ rooms, zoneView = false, onOpenStats }) {
  return (
    <div className="farm-dashboard-rooms">
      {listOrEmpty(rooms).map((room) => (
        <FarmRoom key={room.id} room={room} zoneView={zoneView} onOpenStats={onOpenStats} />
      ))}
    </div>
  );
}
