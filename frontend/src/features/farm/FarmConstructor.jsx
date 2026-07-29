import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  createFarm,
  createFarmZone,
  deleteFarmZone,
  fetchFarmOverview,
  replaceFarmZonePlants,
  replaceFarmZoneScenarios,
  replaceFarmZoneSlots,
  updateFarm,
  updateFarmZone,
} from '../../api/selfService';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import {
  bindingOptionValue,
  optionsForRole,
  optionsWithCurrentBinding,
  parseOptionValue,
  physicalChannelKey,
  resourcePayload,
} from './farmResourceOptions';
import {
  FARM_SCENARIO_TYPES,
  FARM_SLOT_ROLES,
  SCENARIO_LABELS,
  SLOT_ROLE_LABELS,
  buildSlotOccupancy,
  createScenarioDrafts,
  findSlotConflicts,
  listOrEmpty,
  slotForRole,
} from './farmModel';
import { translateApp } from '../../locales/i18n';
import './FarmConstructor.css';

const SCENARIO_FIELDS = {
  BOX_CLIMATE: [
    ['min_c', 'Минимум, °C', 'number'],
    ['max_c', 'Обдув выше, °C', 'number'],
    ['exhaust_off_below_c', 'Обдув выключить ниже, °C', 'number'],
    ['ac_request_above_c', 'Кондиционер выше, °C', 'number'],
    ['ac_clear_below_c', 'Кондиционер выключить ниже, °C', 'number'],
    ['off_delay_minutes', 'Задержка выключения, мин', 'number'],
    ['min_toggle_minutes', 'Интервал переключений, мин', 'number'],
  ],
  LIGHT_SCHEDULE: [
    ['start_time', 'Включить', 'time'],
    ['end_time', 'Выключить', 'time'],
  ],
  WATERING: [
    ['soil_threshold_percent', 'Порог почвы, %', 'number'],
    ['min_interval_hours', 'Минимальная пауза, ч', 'number'],
    ['max_interval_hours', 'Максимальная пауза, ч', 'number'],
    ['run_seconds', 'Длительность, сек', 'number'],
    ['daily_max_seconds', 'Лимит в сутки, сек', 'number'],
  ],
};

const actionKey = (zoneId, section) => `${zoneId}:${section}`;

function initialZoneDraft(zone) {
  const plants = Object.fromEntries(listOrEmpty(zone.plants).map((plant) => [
    plant.id,
    {
      selected: true,
      rate_ml_per_hour: plant.rate_ml_per_hour ?? '',
    },
  ]));
  return {
    name: zone.name || '',
    enabled: zone.enabled !== false,
    slots: Object.fromEntries(FARM_SLOT_ROLES.map((role) => [
      role,
      bindingOptionValue(slotForRole(zone, role)),
    ])),
    plants,
    scenarios: createScenarioDrafts(zone),
  };
}

function formatSlotValue(value) {
  if (value === null || value === undefined || value === '') {
    return translateApp("Нет данных");
  }
  if (typeof value === 'boolean') {
    return value ? translateApp("Да") : translateApp("Нет");
  }
  return String(value);
}

function readinessFor(zone, scenarioType) {
  return zone?.readiness?.[scenarioType]
    || zone?.scenarios?.find((scenario) => scenario.scenario_type === scenarioType)?.readiness
    || { ready: false, reason: translateApp("Назначьте необходимые слоты") };
}

function FarmConstructor() {
  const [overview, setOverview] = useState(null);
  const [farmName, setFarmName] = useState('');
  const [newZoneName, setNewZoneName] = useState('');
  const [zoneDrafts, setZoneDrafts] = useState({});
  const [isLoading, setIsLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const applyOverview = useCallback((payload) => {
    const normalized = payload && typeof payload === 'object' ? payload : {};
    setOverview(normalized);
    setFarmName(normalized.farm?.name || '');
    setZoneDrafts(Object.fromEntries(listOrEmpty(normalized.farm?.zones).map((zone) => [
      zone.id,
      initialZoneDraft(zone),
    ])));
  }, []);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      applyOverview(await fetchFarmOverview());
    } catch (requestError) {
      setError(requestError?.message || translateApp("Не удалось загрузить ферму"));
    } finally {
      setIsLoading(false);
    }
  }, [applyOverview]);

  useEffect(() => {
    load();
  }, [load]);

  const zones = useMemo(() => listOrEmpty(overview?.farm?.zones), [overview]);
  const catalog = overview?.resource_catalog || {};
  const plants = listOrEmpty(catalog.plants);
  const occupancy = useMemo(() => buildSlotOccupancy(zones), [zones]);

  const runAction = async (key, action, successMessage) => {
    setBusy(key);
    setError('');
    setNotice('');
    try {
      const payload = await action();
      if (payload?.farm !== undefined) {
        applyOverview(payload);
      } else {
        await load();
      }
      setNotice(successMessage);
      return true;
    } catch (requestError) {
      setError(requestError?.message || translateApp("Не удалось сохранить изменения"));
      return false;
    } finally {
      setBusy('');
    }
  };

  const patchZoneDraft = (zoneId, patch) => {
    setZoneDrafts((current) => ({
      ...current,
      [zoneId]: {
        ...current[zoneId],
        ...patch,
      },
    }));
  };

  const saveFarm = () => runAction(
    'farm',
    () => updateFarm(farmName.trim()),
    translateApp("Название фермы сохранено"),
  );

  const handleCreateFarm = (event) => {
    event.preventDefault();
    if (!farmName.trim()) return;
    runAction(
      'farm:create',
      () => createFarm(farmName.trim()),
      translateApp("Ферма создана"),
    );
  };

  const handleCreateZone = (event) => {
    event.preventDefault();
    const name = newZoneName.trim();
    if (!name) return;
    runAction(
      'zone:create',
      () => createFarmZone({ name, enabled: true }),
      translateApp("Теплица создана"),
    ).then((saved) => {
      if (saved) setNewZoneName('');
    });
  };

  const saveZone = (zone) => {
    const draft = zoneDrafts[zone.id];
    return runAction(
      actionKey(zone.id, 'zone'),
      () => updateFarmZone(zone.id, {
        name: draft.name.trim(),
        enabled: draft.enabled,
      }),
      translateApp("Настройки теплицы сохранены"),
    );
  };

  const removeZone = (zone) => {
    const confirmed = window.confirm(translateApp(
      "Удалить теплицу «{{value1}}»? Растения останутся без теплицы.",
      { value1: zone.name },
    ));
    if (!confirmed) return;
    runAction(
      actionKey(zone.id, 'delete'),
      () => deleteFarmZone(zone.id),
      translateApp("Теплица удалена"),
    );
  };

  const saveSlots = async (zone) => {
    const valuesByRole = zoneDrafts[zone.id]?.slots || {};
    const selected = Object.entries(valuesByRole)
      .filter(([, value]) => Boolean(value))
      .map(([role, value]) => resourcePayload(role, value))
      .filter(Boolean);
    const keys = selected.map(physicalChannelKey).filter(Boolean);
    if (new Set(keys).size !== keys.length) {
      setError(translateApp("Один физический канал нельзя выбрать для нескольких слотов"));
      return;
    }
    const conflicts = findSlotConflicts(zone.id, valuesByRole, zones);
    let reassign = false;
    if (conflicts.length > 0) {
      const descriptions = conflicts
        .map((conflict) => `${translateApp(SLOT_ROLE_LABELS[conflict.role] || conflict.role)} — ${conflict.zoneName}`)
        .join('\n');
      reassign = window.confirm(translateApp(
        "Канал уже используется в другой теплице:\n{{value1}}\n\nПереназначить его?",
        { value1: descriptions },
      ));
      if (!reassign) return;
    }
    runAction(
      actionKey(zone.id, 'slots'),
      () => replaceFarmZoneSlots(zone.id, selected, reassign),
      translateApp("Слоты сохранены"),
    );
  };

  const savePlants = (zone) => {
    const drafts = zoneDrafts[zone.id]?.plants || {};
    const items = Object.entries(drafts)
      .filter(([, value]) => value.selected)
      .map(([plantId, value]) => ({
        plant_id: Number(plantId),
        rate_ml_per_hour: value.rate_ml_per_hour === ''
          ? null
          : Number(value.rate_ml_per_hour),
      }));
    runAction(
      actionKey(zone.id, 'plants'),
      () => replaceFarmZonePlants(zone.id, items),
      translateApp("Растения теплицы сохранены"),
    );
  };

  const saveScenarios = (zone) => {
    const drafts = zoneDrafts[zone.id]?.scenarios || {};
    runAction(
      actionKey(zone.id, 'scenarios'),
      () => replaceFarmZoneScenarios(
        zone.id,
        FARM_SCENARIO_TYPES.map((scenarioType) => drafts[scenarioType]),
      ),
      translateApp("Сценарии сохранены"),
    );
  };

  if (isLoading) {
    return <AppPageState kind="loading" title={translateApp("Загружаем конструктор…")} />;
  }

  if (error && !overview) {
    return (
      <AppPageState kind="error" title={error}>
        <Button onClick={load}>{translateApp("Повторить")}</Button>
      </AppPageState>
    );
  }

  if (!overview?.farm) {
    return (
      <div className="farm-constructor">
        <AppPageHeader title={translateApp("Конструктор фермы")} />
        <section className="farm-constructor__empty">
          <h2>{translateApp("Создайте свою ферму")}</h2>
          <p>{translateApp("После этого можно добавить теплицы, назначить устройства и включить сценарии.")}</p>
          <form className="farm-constructor__inline-form" onSubmit={handleCreateFarm}>
            <label>
              <span>{translateApp("Название фермы")}</span>
              <input
                value={farmName}
                onChange={(event) => setFarmName(event.target.value)}
                maxLength="120"
                required
              />
            </label>
            <Button type="submit" variant="primary" isLoading={busy === 'farm:create'}>
              {translateApp("Создать ферму")}
            </Button>
          </form>
        </section>
      </div>
    );
  }

  return (
    <div className="farm-constructor">
      <AppPageHeader
        title={translateApp("Конструктор фермы")}
        right={<Link className="gh-btn gh-btn--secondary gh-btn--md" to="/app/settings/devices/">{translateApp("Все устройства")}</Link>}
      />
      <p className="farm-constructor__intro">
        {translateApp("Соберите ферму из теплиц: назначьте каждому слоту физический канал, разместите растения и включите готовые сценарии.")}
      </p>
      {error ? <AppPageState kind="error" title={error} /> : null}
      {notice ? <div className="farm-constructor__notice" role="status">{notice}</div> : null}

      <section className="farm-constructor__farm">
        <div>
          <span>{translateApp("Ферма")}</span>
          <strong>{overview.farm.name}</strong>
          <small>{translateApp("Одна ферма на пользователя")}</small>
        </div>
        <label>
          <span>{translateApp("Название фермы")}</span>
          <input
            value={farmName}
            onChange={(event) => setFarmName(event.target.value)}
            maxLength="120"
          />
        </label>
        <Button onClick={saveFarm} isLoading={busy === 'farm'} disabled={!farmName.trim()}>
          {translateApp("Переименовать")}
        </Button>
      </section>

      <form className="farm-constructor__create-zone" onSubmit={handleCreateZone}>
        <label>
          <span>{translateApp("Новая теплица")}</span>
          <input
            value={newZoneName}
            onChange={(event) => setNewZoneName(event.target.value)}
            placeholder={translateApp("Например, Южная теплица")}
            maxLength="120"
          />
        </label>
        <Button type="submit" variant="primary" isLoading={busy === 'zone:create'} disabled={!newZoneName.trim()}>
          {translateApp("Добавить теплицу")}
        </Button>
      </form>

      {zones.length === 0 ? (
        <AppPageState kind="empty" title={translateApp("Теплиц пока нет")} hint={translateApp("Добавьте первую теплицу выше.")} />
      ) : (
        <div className="farm-constructor__zones">
          {zones.map((zone) => {
            const draft = zoneDrafts[zone.id] || initialZoneDraft(zone);
            return (
              <article className={`farm-zone-editor ${draft.enabled ? '' : 'is-disabled'}`} key={zone.id}>
                <header className="farm-zone-editor__header">
                  <div>
                    <span>{translateApp("Теплица")}</span>
                    <h2>{zone.name}</h2>
                  </div>
                  <div className="farm-zone-editor__zone-fields">
                    <input
                      aria-label={translateApp("Название теплицы")}
                      value={draft.name}
                      maxLength="120"
                      onChange={(event) => patchZoneDraft(zone.id, { name: event.target.value })}
                    />
                    <label className="farm-zone-editor__toggle">
                      <input
                        type="checkbox"
                        checked={draft.enabled}
                        onChange={(event) => patchZoneDraft(zone.id, { enabled: event.target.checked })}
                      />
                      <span>{translateApp("Активна")}</span>
                    </label>
                    <Button
                      size="sm"
                      onClick={() => saveZone(zone)}
                      isLoading={busy === actionKey(zone.id, 'zone')}
                      disabled={!draft.name.trim()}
                    >
                      {translateApp("Сохранить")}
                    </Button>
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() => removeZone(zone)}
                      isLoading={busy === actionKey(zone.id, 'delete')}
                    >
                      {translateApp("Удалить")}
                    </Button>
                  </div>
                </header>

                <section className="farm-zone-editor__section">
                  <div className="farm-zone-editor__section-heading">
                    <div>
                      <h3>{translateApp("Слоты устройств")}</h3>
                      <p>{translateApp("Разные свойства комбинированного датчика можно назначать отдельно.")}</p>
                    </div>
                    <Button
                      size="sm"
                      onClick={() => saveSlots(zone)}
                      isLoading={busy === actionKey(zone.id, 'slots')}
                    >
                      {translateApp("Сохранить слоты")}
                    </Button>
                  </div>
                  <div className="farm-slots">
                    {FARM_SLOT_ROLES.map((role) => {
                      const binding = slotForRole(zone, role);
                      const options = optionsWithCurrentBinding(optionsForRole(role, catalog), binding);
                      const selectedValue = draft.slots[role] || '';
                      const selectedOption = options.find((option) => option.value === selectedValue);
                      const isCurrentBinding = selectedValue === bindingOptionValue(binding);
                      const currentValue = isCurrentBinding
                        ? binding?.current_value
                        : selectedOption?.currentValue;
                      const connectionLabel = isCurrentBinding
                        ? binding?.connection_message
                        : selectedOption?.connectionStatus;
                      const isWarning = isCurrentBinding
                        ? binding?.connection_status === 'warning'
                        : selectedOption?.connectionStatus === 'offline';
                      return (
                        <label className="farm-slot" key={role}>
                          <span className="farm-slot__label">{translateApp(SLOT_ROLE_LABELS[role] || role)}</span>
                          <select
                            value={selectedValue}
                            onChange={(event) => patchZoneDraft(zone.id, {
                              slots: {
                                ...draft.slots,
                                [role]: event.target.value,
                              },
                            })}
                          >
                            <option value="">{translateApp("Не назначено")}</option>
                            {options.map((option) => {
                              const parsed = parseOptionValue(option.value);
                              const occupied = parsed ? occupancy.get(physicalChannelKey(parsed)) : null;
                              const suffix = occupied && occupied.zoneId !== zone.id
                                ? ` · ${translateApp("занято")}: ${occupied.zoneName}`
                                : '';
                              return <option key={option.value} value={option.value}>{option.label}{suffix}</option>;
                            })}
                          </select>
                          <span className={`farm-slot__status ${isWarning ? 'is-warning' : ''}`}>
                            {selectedValue
                              ? `${formatSlotValue(currentValue)} · ${connectionLabel || translateApp("статус неизвестен")}`
                              : translateApp("Слот свободен")}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                </section>

                <section className="farm-zone-editor__section">
                  <div className="farm-zone-editor__section-heading">
                    <div>
                      <h3>{translateApp("Растения")}</h3>
                      <p>{translateApp("Растение может находиться только в одной теплице или оставаться без размещения.")}</p>
                    </div>
                    <Button
                      size="sm"
                      onClick={() => savePlants(zone)}
                      isLoading={busy === actionKey(zone.id, 'plants')}
                    >
                      {translateApp("Сохранить растения")}
                    </Button>
                  </div>
                  {plants.length === 0 ? (
                    <p className="farm-zone-editor__empty">{translateApp("Сначала добавьте растение на странице «Растения».")}</p>
                  ) : (
                    <div className="farm-zone-plants">
                      {plants.map((plant) => {
                        const plantDraft = draft.plants[plant.id] || { selected: false, rate_ml_per_hour: '' };
                        const occupiedZone = zones.find((item) => (
                          item.id !== zone.id
                          && listOrEmpty(item.plants).some((itemPlant) => itemPlant.id === plant.id)
                        ));
                        return (
                          <div className={`farm-zone-plant ${plantDraft.selected ? 'is-selected' : ''}`} key={plant.id}>
                            <label>
                              <input
                                type="checkbox"
                                checked={plantDraft.selected}
                                onChange={(event) => patchZoneDraft(zone.id, {
                                  plants: {
                                    ...draft.plants,
                                    [plant.id]: {
                                      ...plantDraft,
                                      selected: event.target.checked,
                                    },
                                  },
                                })}
                              />
                              <span>
                                <strong>{plant.name}</strong>
                                <small>{occupiedZone ? `${translateApp("Сейчас")}: ${occupiedZone.name}` : translateApp("Без теплицы")}</small>
                              </span>
                            </label>
                            <label>
                              <span>{translateApp("Скорость, мл/ч")}</span>
                              <input
                                type="number"
                                min="1"
                                step="1"
                                disabled={!plantDraft.selected}
                                value={plantDraft.rate_ml_per_hour}
                                onChange={(event) => patchZoneDraft(zone.id, {
                                  plants: {
                                    ...draft.plants,
                                    [plant.id]: {
                                      ...plantDraft,
                                      rate_ml_per_hour: event.target.value,
                                    },
                                  },
                                })}
                              />
                            </label>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </section>

                <section className="farm-zone-editor__section">
                  <div className="farm-zone-editor__section-heading">
                    <div>
                      <h3>{translateApp("Доступные сценарии")}</h3>
                      <p>{translateApp("Backend проверяет готовность по назначенным слотам до включения.")}</p>
                    </div>
                    <Button
                      size="sm"
                      onClick={() => saveScenarios(zone)}
                      isLoading={busy === actionKey(zone.id, 'scenarios')}
                    >
                      {translateApp("Сохранить сценарии")}
                    </Button>
                  </div>
                  <div className="farm-scenarios">
                    {FARM_SCENARIO_TYPES.map((scenarioType) => {
                      const scenario = draft.scenarios[scenarioType];
                      const readiness = readinessFor(zone, scenarioType);
                      const enableBlocked = !readiness.ready && !scenario.enabled;
                      return (
                        <div className={`farm-scenario ${readiness.ready ? 'is-ready' : 'is-unready'}`} key={scenarioType}>
                          <div className="farm-scenario__header">
                            <label>
                              <input
                                type="checkbox"
                                checked={scenario.enabled}
                                disabled={enableBlocked}
                                onChange={(event) => patchZoneDraft(zone.id, {
                                  scenarios: {
                                    ...draft.scenarios,
                                    [scenarioType]: {
                                      ...scenario,
                                      enabled: event.target.checked,
                                    },
                                  },
                                })}
                              />
                              <strong>{translateApp(SCENARIO_LABELS[scenarioType] || scenarioType)}</strong>
                            </label>
                            <span>{readiness.ready ? translateApp("Готово") : readiness.reason}</span>
                          </div>
                          <div className="farm-scenario__fields">
                            {SCENARIO_FIELDS[scenarioType].map(([field, label, type]) => (
                              <label key={field}>
                                <span>{translateApp(label)}</span>
                                <input
                                  type={type}
                                  step={type === 'number' ? '0.1' : undefined}
                                  value={scenario.config?.[field] ?? ''}
                                  onChange={(event) => {
                                    const value = type === 'number'
                                      ? (event.target.value === '' ? '' : Number(event.target.value))
                                      : event.target.value;
                                    patchZoneDraft(zone.id, {
                                      scenarios: {
                                        ...draft.scenarios,
                                        [scenarioType]: {
                                          ...scenario,
                                          config: {
                                            ...(scenario.config || {}),
                                            [field]: value,
                                          },
                                        },
                                      },
                                    });
                                  }}
                                />
                              </label>
                            ))}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </section>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default FarmConstructor;
