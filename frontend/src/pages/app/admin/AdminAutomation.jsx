import React, { useCallback, useEffect, useMemo, useState } from 'react';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import Surface from '../../../components/ui/Surface';
import Button from '../../../components/ui/Button';
import { useAuth } from '../../../features/auth/AuthContext';
import { isSessionExpiredError } from '../../../api/client';
import {
  createAdminAutomationBox,
  createAdminAutomationRoom,
  deleteAdminAutomationBox,
  deleteAdminAutomationRoom,
  fetchAdminAutomationOverview,
  saveAdminAutomationBoxPlants,
  saveAdminAutomationBoxResources,
  saveAdminAutomationBoxScenarios,
  saveAdminAutomationRoomResources,
  saveAdminAutomationRoomScenarios,
  updateAdminAutomationBox,
  updateAdminAutomationRoom,
} from '../../../api/admin';
import {
  bindingOptionValue,
  optionsForRole,
  optionsWithCurrentBinding,
  resourceBindingForRole,
  resourcePayload,
} from './adminAutomationResources';
import './AdminPages.css';

const ROOM_SCOPE = 'ROOM';
const BOX_SCOPE = 'BOX';

const ROOM_RESOURCE_ROLES = ['AC_SWITCH'];
const BOX_RESOURCE_ROLES = [
  'AIR_TEMPERATURE_SENSOR',
  'EXHAUST_SWITCH',
  'LIGHT_SWITCH',
  'SOIL_MOISTURE_SENSOR',
  'WATER_PUMP',
];
const ROOM_SCENARIOS = ['ROOM_CLIMATE'];
const BOX_SCENARIOS = ['BOX_CLIMATE', 'LIGHT_SCHEDULE', 'WATERING'];

const ROLE_LABELS = {
  AC_SWITCH: 'Кондиционер',
  AIR_TEMPERATURE_SENSOR: 'Температура воздуха',
  EXHAUST_SWITCH: 'Вытяжка',
  LIGHT_SWITCH: 'Свет',
  SOIL_MOISTURE_SENSOR: 'Влажность почвы',
  WATER_PUMP: 'Насос полива',
};

const SCENARIO_LABELS = {
  ROOM_CLIMATE: 'Климат помещения',
  BOX_CLIMATE: 'Климат бокса',
  LIGHT_SCHEDULE: 'Свет',
  WATERING: 'Полив',
};

const SCENARIO_FIELDS = {
  ROOM_CLIMATE: [
    ['off_delay_minutes', 'Задержка выключения, мин', 'number'],
    ['min_toggle_minutes', 'Мин. интервал, мин', 'number'],
  ],
  BOX_CLIMATE: [
    ['min_c', 'Мин., °C', 'number'],
    ['max_c', 'Вкл. вытяжку выше, °C', 'number'],
    ['exhaust_off_below_c', 'Вытяжку выкл. ниже, °C', 'number'],
    ['ac_request_above_c', 'Кондиционер выше, °C', 'number'],
    ['ac_clear_below_c', 'Снять запрос ниже, °C', 'number'],
  ],
  LIGHT_SCHEDULE: [
    ['start_time', 'Включить', 'time'],
    ['end_time', 'Выключить', 'time'],
  ],
  WATERING: [
    ['soil_threshold_percent', 'Порог почвы, %', 'number'],
    ['max_interval_hours', 'Макс. пауза, ч', 'number'],
    ['run_seconds', 'Длительность, сек', 'number'],
    ['min_interval_hours', 'Мин. пауза, ч', 'number'],
    ['daily_max_seconds', 'Лимит в день, сек', 'number'],
  ],
};

function scopeKey(scopeType, scopeId) {
  return `${scopeType}:${scopeId}`;
}

function listOrEmpty(value) {
  return Array.isArray(value) ? value : [];
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ru-RU');
}

function formatValue(value) {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'boolean') return value ? 'Да' : 'Нет';
  return String(value);
}

function initialResourceDraft(resources) {
  const draft = {};
  listOrEmpty(resources).forEach((resource) => {
    draft[resource.role] = bindingOptionValue(resource);
  });
  return draft;
}

function initialScenarioDraft(scenarios) {
  const draft = {};
  listOrEmpty(scenarios).forEach((scenario) => {
    draft[scenario.scenario_type] = {
      enabled: Boolean(scenario.enabled),
      config: { ...(scenario.config || {}) },
    };
  });
  return draft;
}

function ReadinessBadge({ readiness }) {
  const ready = Boolean(readiness?.ready);
  return (
    <span className={ready ? 'admin-automation-badge is-ready' : 'admin-automation-badge is-unready'}>
      {ready ? 'Готово' : (readiness?.reason || 'Не готово')}
    </span>
  );
}

function AdminAutomation() {
  const { token } = useAuth();
  const [overview, setOverview] = useState(null);
  const [roomDrafts, setRoomDrafts] = useState({});
  const [boxDrafts, setBoxDrafts] = useState({});
  const [plantDrafts, setPlantDrafts] = useState({});
  const [resourceDrafts, setResourceDrafts] = useState({});
  const [scenarioDrafts, setScenarioDrafts] = useState({});
  const [newRoomName, setNewRoomName] = useState('');
  const [newBoxNames, setNewBoxNames] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [actionKey, setActionKey] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const applyOverview = useCallback((data) => {
    const normalized = data && typeof data === 'object' ? data : {};
    const rooms = listOrEmpty(normalized.rooms);
    setOverview({
      rooms,
      resource_catalog: normalized.resource_catalog || {},
      last_actions: listOrEmpty(normalized.last_actions),
      settings: normalized.settings || {},
    });

    const nextRooms = {};
    const nextBoxes = {};
    const nextPlants = {};
    const nextResources = {};
    const nextScenarios = {};
    rooms.forEach((room) => {
      nextRooms[room.id] = { name: room.name || '', enabled: room.enabled !== false };
      nextResources[scopeKey(ROOM_SCOPE, room.id)] = initialResourceDraft(room.resources);
      nextScenarios[scopeKey(ROOM_SCOPE, room.id)] = initialScenarioDraft(room.scenarios);
      listOrEmpty(room.boxes).forEach((box) => {
        nextBoxes[box.id] = { name: box.name || '', enabled: box.enabled !== false };
        nextPlants[box.id] = listOrEmpty(box.plants).map((plant) => plant.id);
        nextResources[scopeKey(BOX_SCOPE, box.id)] = initialResourceDraft(box.resources);
        nextScenarios[scopeKey(BOX_SCOPE, box.id)] = initialScenarioDraft(box.scenarios);
      });
    });
    setRoomDrafts(nextRooms);
    setBoxDrafts(nextBoxes);
    setPlantDrafts(nextPlants);
    setResourceDrafts(nextResources);
    setScenarioDrafts(nextScenarios);
  }, []);

  const loadOverview = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await fetchAdminAutomationOverview(token);
      applyOverview(data);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось загрузить автоматизацию');
    } finally {
      setIsLoading(false);
    }
  }, [applyOverview, token]);

  useEffect(() => {
    loadOverview();
  }, [loadOverview]);

  const catalog = overview?.resource_catalog || {};
  const plants = useMemo(() => listOrEmpty(catalog.plants), [catalog.plants]);

  const runAction = useCallback(async (key, fn, successMessage) => {
    if (actionKey) return;
    setActionKey(key);
    setError('');
    setNotice('');
    try {
      const data = await fn();
      if (data?.rooms) {
        applyOverview(data);
      } else {
        await loadOverview();
      }
      setNotice(successMessage);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось выполнить действие');
    } finally {
      setActionKey('');
    }
  }, [actionKey, applyOverview, loadOverview]);

  const handleCreateRoom = useCallback(() => {
    const name = newRoomName.trim();
    if (!name) return;
    runAction('room:create', () => createAdminAutomationRoom({ name, enabled: true }, token), 'Помещение создано');
    setNewRoomName('');
  }, [newRoomName, runAction, token]);

  const handleSaveRoom = useCallback((roomId) => {
    const draft = roomDrafts[roomId];
    if (!draft) return;
    runAction(`room:${roomId}:save`, () => updateAdminAutomationRoom(roomId, draft, token), 'Помещение сохранено');
  }, [roomDrafts, runAction, token]);

  const handleDeleteRoom = useCallback((roomId) => {
    runAction(`room:${roomId}:delete`, () => deleteAdminAutomationRoom(roomId, token), 'Помещение удалено');
  }, [runAction, token]);

  const handleCreateBox = useCallback((roomId) => {
    const name = (newBoxNames[roomId] || '').trim();
    if (!name) return;
    runAction(`room:${roomId}:box:create`, () => createAdminAutomationBox(roomId, { name, enabled: true }, token), 'Бокс создан');
    setNewBoxNames((prev) => ({ ...prev, [roomId]: '' }));
  }, [newBoxNames, runAction, token]);

  const handleSaveBox = useCallback((boxId) => {
    const draft = boxDrafts[boxId];
    if (!draft) return;
    runAction(`box:${boxId}:save`, () => updateAdminAutomationBox(boxId, draft, token), 'Бокс сохранен');
  }, [boxDrafts, runAction, token]);

  const handleDeleteBox = useCallback((boxId) => {
    runAction(`box:${boxId}:delete`, () => deleteAdminAutomationBox(boxId, token), 'Бокс удален');
  }, [runAction, token]);

  const handleSavePlants = useCallback((boxId) => {
    const plantIds = plantDrafts[boxId] || [];
    runAction(`box:${boxId}:plants`, () => saveAdminAutomationBoxPlants(boxId, plantIds, token), 'Растения бокса сохранены');
  }, [plantDrafts, runAction, token]);

  const saveResources = useCallback((scopeType, scopeId, roles) => {
    const key = scopeKey(scopeType, scopeId);
    const resources = roles
      .map((role) => resourcePayload(role, resourceDrafts[key]?.[role]))
      .filter(Boolean);
    const request = scopeType === ROOM_SCOPE
      ? () => saveAdminAutomationRoomResources(scopeId, resources, token)
      : () => saveAdminAutomationBoxResources(scopeId, resources, token);
    runAction(`${key}:resources`, request, 'Ресурсы сохранены');
  }, [resourceDrafts, runAction, token]);

  const saveScenarios = useCallback((scopeType, scopeId, scenarioTypes) => {
    const key = scopeKey(scopeType, scopeId);
    const drafts = scenarioDrafts[key] || {};
    const scenarios = scenarioTypes.map((scenarioType) => ({
      scenario_type: scenarioType,
      enabled: Boolean(drafts[scenarioType]?.enabled),
      config: drafts[scenarioType]?.config || {},
    }));
    const request = scopeType === ROOM_SCOPE
      ? () => saveAdminAutomationRoomScenarios(scopeId, scenarios, token)
      : () => saveAdminAutomationBoxScenarios(scopeId, scenarios, token);
    runAction(`${key}:scenarios`, request, 'Сценарии сохранены');
  }, [runAction, scenarioDrafts, token]);

  const updateResourceDraft = useCallback((scopeType, scopeId, role, value) => {
    const key = scopeKey(scopeType, scopeId);
    setResourceDrafts((prev) => ({
      ...prev,
      [key]: {
        ...(prev[key] || {}),
        [role]: value,
      },
    }));
  }, []);

  const updateScenarioDraft = useCallback((scopeType, scopeId, scenarioType, patch) => {
    const key = scopeKey(scopeType, scopeId);
    setScenarioDrafts((prev) => {
      const scopeDraft = prev[key] || {};
      const current = scopeDraft[scenarioType] || { enabled: false, config: {} };
      return {
        ...prev,
        [key]: {
          ...scopeDraft,
          [scenarioType]: {
            ...current,
            ...patch,
            config: {
              ...(current.config || {}),
              ...(patch.config || {}),
            },
          },
        },
      };
    });
  }, []);

  const renderResources = (scopeType, scopeId, roles, resources) => {
    const key = scopeKey(scopeType, scopeId);
    return (
      <div className="admin-automation-grid">
        {roles.map((role) => {
          const binding = resourceBindingForRole(resources, role);
          const options = optionsWithCurrentBinding(optionsForRole(role, catalog), binding);
          return (
            <label className="admin-automation-field" key={role}>
              <span>{ROLE_LABELS[role] || role}</span>
              <select
                className="admin-select"
                value={resourceDrafts[key]?.[role] || bindingOptionValue(binding)}
                onChange={(event) => updateResourceDraft(scopeType, scopeId, role, event.target.value)}
              >
                <option value="">Не выбрано</option>
                {options.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
          );
        })}
        <div className="admin-automation-actions">
          <Button
            type="button"
            size="sm"
            onClick={() => saveResources(scopeType, scopeId, roles)}
            disabled={Boolean(actionKey)}
          >
            Сохранить ресурсы
          </Button>
        </div>
      </div>
    );
  };

  const renderScenarios = (scopeType, scopeId, scenarioTypes) => {
    const key = scopeKey(scopeType, scopeId);
    const scopeDraft = scenarioDrafts[key] || {};
    return (
      <div className="admin-automation-scenarios">
        {scenarioTypes.map((scenarioType) => {
          const draft = scopeDraft[scenarioType] || { enabled: false, config: {} };
          const fields = SCENARIO_FIELDS[scenarioType] || [];
          return (
            <div className="admin-automation-scenario" key={scenarioType}>
              <label className="admin-automation-toggle">
                <input
                  type="checkbox"
                  checked={Boolean(draft.enabled)}
                  onChange={(event) => updateScenarioDraft(scopeType, scopeId, scenarioType, { enabled: event.target.checked })}
                />
                <span>{SCENARIO_LABELS[scenarioType] || scenarioType}</span>
              </label>
              <div className="admin-automation-grid">
                {fields.map(([field, label, type]) => (
                  <label className="admin-automation-field" key={field}>
                    <span>{label}</span>
                    <input
                      className="admin-input"
                      type={type}
                      step={type === 'number' ? '0.1' : undefined}
                      value={draft.config?.[field] ?? ''}
                      onChange={(event) => {
                        const value = type === 'number' ? Number(event.target.value) : event.target.value;
                        updateScenarioDraft(scopeType, scopeId, scenarioType, { config: { [field]: value } });
                      }}
                    />
                  </label>
                ))}
              </div>
            </div>
          );
        })}
        <Button
          type="button"
          size="sm"
          onClick={() => saveScenarios(scopeType, scopeId, scenarioTypes)}
          disabled={Boolean(actionKey)}
        >
          Сохранить сценарии
        </Button>
      </div>
    );
  };

  return (
    <div className="admin-page">
      <AppPageHeader title="Автоматизация" />
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <div className="admin-error">{error}</div>}
      {notice && <div className="admin-notice">{notice}</div>}

      <Surface variant="card" padding="md" className="admin-section">
        <h3 className="admin-section__title">Помещения</h3>
        <div className="admin-row-actions">
          <input
            className="admin-input"
            type="text"
            value={newRoomName}
            placeholder="Название помещения"
            onChange={(event) => setNewRoomName(event.target.value)}
          />
          <Button type="button" onClick={handleCreateRoom} disabled={!newRoomName.trim() || Boolean(actionKey)}>
            Добавить
          </Button>
          <Button type="button" variant="secondary" onClick={loadOverview} disabled={isLoading}>
            Обновить
          </Button>
        </div>
      </Surface>

      {listOrEmpty(overview?.rooms).length === 0 && !isLoading ? (
        <Surface variant="card" padding="md" className="admin-section">
          <div className="admin-table__empty">Помещений пока нет</div>
        </Surface>
      ) : listOrEmpty(overview?.rooms).map((room) => {
        const roomDraft = roomDrafts[room.id] || { name: room.name || '', enabled: room.enabled !== false };
        return (
          <Surface variant="card" padding="md" className="admin-section" key={room.id}>
            <div className="admin-automation-heading">
              <div className="admin-row-actions">
                <input
                  className="admin-input"
                  type="text"
                  value={roomDraft.name}
                  onChange={(event) => setRoomDrafts((prev) => ({
                    ...prev,
                    [room.id]: { ...roomDraft, name: event.target.value },
                  }))}
                />
                <label className="admin-automation-toggle">
                  <input
                    type="checkbox"
                    checked={roomDraft.enabled}
                    onChange={(event) => setRoomDrafts((prev) => ({
                      ...prev,
                      [room.id]: { ...roomDraft, enabled: event.target.checked },
                    }))}
                  />
                  <span>Активно</span>
                </label>
                <Button type="button" size="sm" onClick={() => handleSaveRoom(room.id)} disabled={Boolean(actionKey)}>
                  Сохранить
                </Button>
                <Button type="button" size="sm" variant="danger" onClick={() => handleDeleteRoom(room.id)} disabled={Boolean(actionKey)}>
                  Удалить
                </Button>
              </div>
              <span className="admin-event-list__meta">Обновлено: {formatDateTime(room.updated_at)}</span>
            </div>

            <div className="admin-automation-block">
              <h4>Ресурсы помещения</h4>
              {renderResources(ROOM_SCOPE, room.id, ROOM_RESOURCE_ROLES, room.resources)}
            </div>

            <div className="admin-automation-block">
              <h4>Сценарии помещения</h4>
              {listOrEmpty(room.scenarios).map((scenario) => (
                <ReadinessBadge key={scenario.scenario_type} readiness={scenario.readiness} />
              ))}
              {renderScenarios(ROOM_SCOPE, room.id, ROOM_SCENARIOS)}
            </div>

            <div className="admin-automation-block">
              <h4>Боксы</h4>
              <div className="admin-row-actions">
                <input
                  className="admin-input"
                  type="text"
                  value={newBoxNames[room.id] || ''}
                  placeholder="Название бокса"
                  onChange={(event) => setNewBoxNames((prev) => ({ ...prev, [room.id]: event.target.value }))}
                />
                <Button
                  type="button"
                  size="sm"
                  onClick={() => handleCreateBox(room.id)}
                  disabled={!(newBoxNames[room.id] || '').trim() || Boolean(actionKey)}
                >
                  Добавить бокс
                </Button>
              </div>

              <div className="admin-automation-boxes">
                {listOrEmpty(room.boxes).map((box) => {
                  const boxDraft = boxDrafts[box.id] || { name: box.name || '', enabled: box.enabled !== false };
                  return (
                    <div className="admin-automation-box" key={box.id}>
                      <div className="admin-automation-heading">
                        <div className="admin-row-actions">
                          <input
                            className="admin-input"
                            type="text"
                            value={boxDraft.name}
                            onChange={(event) => setBoxDrafts((prev) => ({
                              ...prev,
                              [box.id]: { ...boxDraft, name: event.target.value },
                            }))}
                          />
                          <label className="admin-automation-toggle">
                            <input
                              type="checkbox"
                              checked={boxDraft.enabled}
                              onChange={(event) => setBoxDrafts((prev) => ({
                                ...prev,
                                [box.id]: { ...boxDraft, enabled: event.target.checked },
                              }))}
                            />
                            <span>Активно</span>
                          </label>
                          <Button type="button" size="sm" onClick={() => handleSaveBox(box.id)} disabled={Boolean(actionKey)}>
                            Сохранить
                          </Button>
                          <Button type="button" size="sm" variant="danger" onClick={() => handleDeleteBox(box.id)} disabled={Boolean(actionKey)}>
                            Удалить
                          </Button>
                        </div>
                        <div className="admin-row-actions">
                          {Object.entries(box.readiness || {}).map(([scenarioType, readiness]) => (
                            <ReadinessBadge key={scenarioType} readiness={readiness} />
                          ))}
                        </div>
                      </div>

                      <div className="admin-automation-block">
                        <h4>Растения</h4>
                        <div className="admin-row-actions">
                          <select
                            className="admin-select admin-automation-multiselect"
                            multiple
                            value={listOrEmpty(plantDrafts[box.id]).map(String)}
                            onChange={(event) => {
                              const values = Array.from(event.target.selectedOptions).map((option) => Number(option.value));
                              setPlantDrafts((prev) => ({ ...prev, [box.id]: values }));
                            }}
                          >
                            {plants.map((plant) => (
                              <option key={plant.id} value={plant.id}>
                                {plant.name}{plant.owner_email ? ` · ${plant.owner_email}` : ''}
                              </option>
                            ))}
                          </select>
                          <Button type="button" size="sm" onClick={() => handleSavePlants(box.id)} disabled={Boolean(actionKey)}>
                            Сохранить растения
                          </Button>
                        </div>
                      </div>

                      <div className="admin-automation-block">
                        <h4>Ресурсы бокса</h4>
                        {renderResources(BOX_SCOPE, box.id, BOX_RESOURCE_ROLES, box.resources)}
                      </div>

                      <div className="admin-automation-block">
                        <h4>Сценарии бокса</h4>
                        {renderScenarios(BOX_SCOPE, box.id, BOX_SCENARIOS)}
                      </div>

                      <div className="admin-automation-block">
                        <h4>Последние действия</h4>
                        {listOrEmpty(box.last_actions).length === 0 ? (
                          <span className="admin-event-list__empty">Нет действий</span>
                        ) : (
                          <div className="admin-event-list">
                            {listOrEmpty(box.last_actions).map((item) => (
                              <div className="admin-event-list__item" key={item.id}>
                                <span className="admin-event-list__title">
                                  {item.action} · {formatValue(item.result)}
                                </span>
                                <span className="admin-event-list__meta">
                                  {formatDateTime(item.created_at)} · {item.reason || '-'}
                                </span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </Surface>
        );
      })}
    </div>
  );
}

export default AdminAutomation;
