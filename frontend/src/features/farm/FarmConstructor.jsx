import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  fetchFarmsOverview,
  replaceGreenhousePlants,
  replaceGreenhouseScenarios,
  replaceGreenhouseSlots,
  replaceUserFarmSlots,
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
  FARM_ROOM_SLOT_ROLES,
  FARM_SCENARIO_TYPES,
  FARM_SLOT_ROLES,
  SCENARIO_LABELS,
  SLOT_ROLE_LABELS,
  buildSlotOccupancy,
  createScenarioDrafts,
  findSlotConflicts,
  listOrEmpty,
  overviewFarms,
  overviewGreenhouses,
  slotForRole,
} from './farmModel';
import { translateApp } from '../../locales/i18n';
import './FarmConstructor.css';

const SCENARIO_FIELDS = {
  BOX_CLIMATE: [
    ['min_c', 'Минимум, °C', 'number'],
    ['max_c', 'Обдув выше, °C', 'number'],
    ['exhaust_off_below_c', 'Обдув выключить ниже, °C', 'number'],
    ['ac_request_above_c', 'Запрос охлаждения выше, °C', 'number'],
    ['ac_clear_below_c', 'Снять запрос ниже, °C', 'number'],
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

const actionKey = (scopeId, section) => `${scopeId}:${section}`;

function initialSlotDraft(scope) {
  const assignedRoles = listOrEmpty(scope?.slots).map((slot) => slot.role);
  return {
    slots: Object.fromEntries(assignedRoles.map((role) => [
      role,
      bindingOptionValue(slotForRole(scope, role)),
    ])),
    visibleRoles: assignedRoles,
    newSlotRole: '',
  };
}

function initialGreenhouseDraft(greenhouse) {
  const plants = Object.fromEntries(listOrEmpty(greenhouse.plants).map((plant) => [
    plant.id,
    {
      selected: true,
      rate_ml_per_hour: plant.rate_ml_per_hour ?? '',
    },
  ]));
  return {
    ...initialSlotDraft(greenhouse),
    plants,
    scenarios: createScenarioDrafts(greenhouse),
  };
}

function formatSlotValue(value) {
  if (value === null || value === undefined || value === '') {
    return translateApp('Нет данных');
  }
  if (typeof value === 'boolean') {
    return value ? translateApp('Да') : translateApp('Нет');
  }
  return String(value);
}

function readinessFor(greenhouse, scenarioType) {
  return greenhouse?.readiness?.[scenarioType]
    || greenhouse?.scenarios?.find((scenario) => scenario.scenario_type === scenarioType)?.readiness
    || { ready: false, reason: translateApp('Назначьте необходимые слоты') };
}

function SlotEditor({
  scope,
  scopeType,
  roles,
  draft,
  catalog,
  occupancy,
  onChange,
  onSave,
  isSaving,
}) {
  const visibleRoles = listOrEmpty(draft?.visibleRoles);
  const availableRoles = roles.filter((role) => !visibleRoles.includes(role));

  const addSlot = () => {
    if (!draft?.newSlotRole) return;
    onChange({
      visibleRoles: [...visibleRoles, draft.newSlotRole],
      slots: { ...(draft.slots || {}), [draft.newSlotRole]: '' },
      newSlotRole: '',
    });
  };

  const removeSlot = (role) => {
    const nextSlots = { ...(draft.slots || {}) };
    delete nextSlots[role];
    onChange({
      visibleRoles: visibleRoles.filter((item) => item !== role),
      slots: nextSlots,
    });
  };

  return (
    <section className="farm-zone-editor__section">
      <div className="farm-zone-editor__section-heading">
        <h3>{translateApp('Слоты устройств')}</h3>
        <Button size="sm" onClick={onSave} isLoading={isSaving}>
          {translateApp('Сохранить слоты')}
        </Button>
      </div>

      {visibleRoles.length > 0 ? (
        <div className="farm-slots">
          {visibleRoles.map((role) => {
            const binding = slotForRole(scope, role);
            const options = optionsWithCurrentBinding(
              optionsForRole(role, catalog),
              binding,
              translateApp(SLOT_ROLE_LABELS[role] || role),
            );
            const selectedValue = draft.slots?.[role] || '';
            const selectedOption = options.find((option) => option.value === selectedValue);
            const isCurrentBinding = selectedValue === bindingOptionValue(binding);
            const currentValue = isCurrentBinding ? binding?.current_value : selectedOption?.currentValue;
            const connectionLabel = isCurrentBinding
              ? binding?.connection_message
              : selectedOption?.connectionStatus;
            const isWarning = isCurrentBinding
              ? binding?.connection_status === 'warning'
              : selectedOption?.connectionStatus === 'offline';
            return (
              <div className="farm-slot" key={role}>
                <div className="farm-slot__heading">
                  <span className="farm-slot__label">
                    {translateApp(SLOT_ROLE_LABELS[role] || role)}
                  </span>
                  <button type="button" onClick={() => removeSlot(role)}>
                    {translateApp('Убрать')}
                  </button>
                </div>
                <select
                  aria-label={translateApp(SLOT_ROLE_LABELS[role] || role)}
                  value={selectedValue}
                  onChange={(event) => onChange({
                    slots: {
                      ...(draft.slots || {}),
                      [role]: event.target.value,
                    },
                  })}
                >
                  <option value="">{translateApp('Не назначено')}</option>
                  {options.map((option) => {
                    const parsed = parseOptionValue(option.value);
                    const occupied = parsed ? occupancy.get(physicalChannelKey(parsed)) : null;
                    const isCurrent = occupied
                      && occupied.zoneId === scope.id
                      && occupied.scopeType === scopeType;
                    const suffix = occupied && !isCurrent
                      ? ` · ${translateApp('занято')}: ${occupied.zoneName}`
                      : '';
                    return (
                      <option key={option.value} value={option.value}>
                        {option.label}{suffix}
                      </option>
                    );
                  })}
                </select>
                {selectedValue ? (
                  <span className={`farm-slot__status ${isWarning ? 'is-warning' : ''}`}>
                    {formatSlotValue(currentValue)}
                    {' · '}
                    {connectionLabel || translateApp('статус неизвестен')}
                  </span>
                ) : null}
              </div>
            );
          })}
        </div>
      ) : null}

      {availableRoles.length > 0 ? (
        <div className="farm-slot-add">
          <select
            aria-label={translateApp('Тип нового слота')}
            value={draft?.newSlotRole || ''}
            onChange={(event) => onChange({ newSlotRole: event.target.value })}
          >
            <option value="">{translateApp('Выберите тип слота')}</option>
            {availableRoles.map((role) => (
              <option key={role} value={role}>
                {translateApp(SLOT_ROLE_LABELS[role] || role)}
              </option>
            ))}
          </select>
          <Button size="sm" variant="secondary" onClick={addSlot} disabled={!draft?.newSlotRole}>
            {translateApp('Добавить слот')}
          </Button>
        </div>
      ) : null}
    </section>
  );
}

function FarmConstructor() {
  const [overview, setOverview] = useState(null);
  const [selectedFarmId, setSelectedFarmId] = useState('');
  const [farmDrafts, setFarmDrafts] = useState({});
  const [greenhouseDrafts, setGreenhouseDrafts] = useState({});
  const [isLoading, setIsLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const applyOverview = useCallback((payload) => {
    const normalized = payload && typeof payload === 'object' ? payload : {};
    const farms = overviewFarms(normalized);
    setOverview(normalized);
    setSelectedFarmId((current) => (
      farms.some((farm) => String(farm.id) === String(current))
        ? current
        : (farms[0]?.id ?? '')
    ));
    setFarmDrafts(Object.fromEntries(farms.map((farm) => [
      farm.id,
      initialSlotDraft(farm),
    ])));
    setGreenhouseDrafts(Object.fromEntries(
      farms.flatMap((farm) => listOrEmpty(farm.greenhouses))
        .map((greenhouse) => [greenhouse.id, initialGreenhouseDraft(greenhouse)]),
    ));
  }, []);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      applyOverview(await fetchFarmsOverview());
    } catch (requestError) {
      setError(requestError?.message || translateApp('Не удалось загрузить фермы'));
    } finally {
      setIsLoading(false);
    }
  }, [applyOverview]);

  useEffect(() => {
    load();
  }, [load]);

  const farms = overviewFarms(overview);
  const selectedFarm = farms.find((farm) => String(farm.id) === String(selectedFarmId)) || null;
  const greenhouses = listOrEmpty(selectedFarm?.greenhouses);
  const catalog = overview?.resource_catalog || {};
  const plants = listOrEmpty(catalog.plants);
  const occupancyScopes = useMemo(() => farms.flatMap((farm) => [
    { ...farm, name: farm.name, scope_type: 'ROOM' },
    ...listOrEmpty(farm.greenhouses).map((greenhouse) => ({
      ...greenhouse,
      name: `${farm.name} · ${greenhouse.name}`,
      scope_type: 'BOX',
    })),
  ]), [farms]);
  const occupancy = useMemo(() => buildSlotOccupancy(occupancyScopes), [occupancyScopes]);
  const allGreenhouses = overviewGreenhouses(overview);

  const runAction = async (key, action, successMessage) => {
    setBusy(key);
    setError('');
    setNotice('');
    try {
      applyOverview(await action());
      setNotice(successMessage);
    } catch (requestError) {
      setError(requestError?.message || translateApp('Не удалось сохранить изменения'));
    } finally {
      setBusy('');
    }
  };

  const patchFarmDraft = (farmId, patch) => {
    setFarmDrafts((current) => ({
      ...current,
      [farmId]: { ...current[farmId], ...patch },
    }));
  };

  const patchGreenhouseDraft = (greenhouseId, patch) => {
    setGreenhouseDrafts((current) => ({
      ...current,
      [greenhouseId]: { ...current[greenhouseId], ...patch },
    }));
  };

  const saveSlots = (scope, scopeType, draft, save) => {
    const valuesByRole = Object.fromEntries(
      listOrEmpty(draft?.visibleRoles).map((role) => [role, draft.slots?.[role] || '']),
    );
    const selected = Object.entries(valuesByRole)
      .filter(([, value]) => Boolean(value))
      .map(([role, value]) => resourcePayload(role, value))
      .filter(Boolean);
    const keys = selected.map(physicalChannelKey).filter(Boolean);
    if (new Set(keys).size !== keys.length) {
      setError(translateApp('Один физический канал нельзя выбрать для нескольких слотов'));
      return;
    }
    const conflicts = findSlotConflicts(scope.id, valuesByRole, occupancyScopes, scopeType);
    let reassign = false;
    if (conflicts.length > 0) {
      const descriptions = conflicts
        .map((conflict) => (
          `${translateApp(SLOT_ROLE_LABELS[conflict.role] || conflict.role)} — ${conflict.zoneName}`
        ))
        .join('\n');
      reassign = window.confirm(translateApp(
        'Канал уже используется в другом месте:\n{{value1}}\n\nПереназначить его?',
        { value1: descriptions },
      ));
      if (!reassign) return;
    }
    runAction(
      actionKey(scope.id, `${scopeType}:slots`),
      () => save(selected, reassign),
      translateApp('Слоты сохранены'),
    );
  };

  const savePlants = (greenhouse) => {
    const drafts = greenhouseDrafts[greenhouse.id]?.plants || {};
    const items = Object.entries(drafts)
      .filter(([, value]) => value.selected)
      .map(([plantId, value]) => ({
        plant_id: Number(plantId),
        rate_ml_per_hour: value.rate_ml_per_hour === ''
          ? null
          : Number(value.rate_ml_per_hour),
      }));
    runAction(
      actionKey(greenhouse.id, 'plants'),
      () => replaceGreenhousePlants(greenhouse.id, items),
      translateApp('Растения теплицы сохранены'),
    );
  };

  const saveScenarios = (greenhouse) => {
    const drafts = greenhouseDrafts[greenhouse.id]?.scenarios || {};
    runAction(
      actionKey(greenhouse.id, 'scenarios'),
      () => replaceGreenhouseScenarios(
        greenhouse.id,
        FARM_SCENARIO_TYPES.map((scenarioType) => drafts[scenarioType]),
      ),
      translateApp('Сценарии сохранены'),
    );
  };

  if (isLoading) {
    return <AppPageState kind="loading" title={translateApp('Загружаем конструктор…')} />;
  }

  if (error && !overview) {
    return (
      <AppPageState kind="error" title={error}>
        <Button onClick={load}>{translateApp('Повторить')}</Button>
      </AppPageState>
    );
  }

  if (farms.length === 0) {
    return (
      <div className="farm-constructor">
        <AppPageHeader title={translateApp('Конструктор фермы')} />
        <AppPageState
          kind="empty"
          title={translateApp('Сначала создайте ферму')}
          hint={translateApp('Названия и структура ферм настраиваются во вкладке «Зоны».')}
        >
          <Link className="gh-btn gh-btn--primary gh-btn--md" to="/app/settings/zones/">
            {translateApp('Открыть настройки зон')}
          </Link>
        </AppPageState>
      </div>
    );
  }

  const farmDraft = farmDrafts[selectedFarm?.id] || initialSlotDraft(selectedFarm);

  return (
    <div className="farm-constructor">
      <AppPageHeader
        title={translateApp('Конструктор фермы')}
        right={(
          <Link className="gh-btn gh-btn--secondary gh-btn--md" to="/app/settings/devices/">
            {translateApp('Все устройства')}
          </Link>
        )}
      />
      <p className="farm-constructor__intro">
        {translateApp('Назначьте оборудование, разместите растения и настройте сценарии существующих теплиц.')}
      </p>
      {error ? <AppPageState kind="error" title={error} /> : null}
      {notice ? <div className="farm-constructor__notice" role="status">{notice}</div> : null}

      <section className="farm-constructor__farm">
        <label>
          <span>{translateApp('Ферма')}</span>
          <select
            value={selectedFarmId}
            onChange={(event) => setSelectedFarmId(event.target.value)}
          >
            {farms.map((farm) => (
              <option key={farm.id} value={farm.id}>{farm.name}</option>
            ))}
          </select>
        </label>
        <Link className="gh-btn gh-btn--secondary gh-btn--md" to="/app/settings/zones/">
          {translateApp('Изменить структуру')}
        </Link>
      </section>

      <article className="farm-zone-editor farm-zone-editor--farm">
        <header className="farm-zone-editor__header">
          <h2>{selectedFarm.name}</h2>
          <span className={selectedFarm.enabled ? 'status-chip is-online' : 'status-chip'}>
            {selectedFarm.enabled ? translateApp('Активна') : translateApp('Выключена')}
          </span>
        </header>
        <SlotEditor
          scope={selectedFarm}
          scopeType="ROOM"
          roles={FARM_ROOM_SLOT_ROLES}
          draft={farmDraft}
          catalog={catalog}
          occupancy={occupancy}
          onChange={(patch) => patchFarmDraft(selectedFarm.id, patch)}
          onSave={() => saveSlots(
            selectedFarm,
            'ROOM',
            farmDraft,
            (slots, reassign) => replaceUserFarmSlots(selectedFarm.id, slots, reassign),
          )}
          isSaving={busy === actionKey(selectedFarm.id, 'ROOM:slots')}
        />
      </article>

      {greenhouses.length === 0 ? (
        <AppPageState
          kind="empty"
          title={translateApp('В этой ферме пока нет теплиц')}
          hint={translateApp('Добавьте теплицу во вкладке «Зоны».')}
        >
          <Link className="gh-btn gh-btn--primary gh-btn--md" to="/app/settings/zones/">
            {translateApp('Открыть настройки зон')}
          </Link>
        </AppPageState>
      ) : (
        <div className="farm-constructor__zones">
          {greenhouses.map((greenhouse) => {
            const draft = greenhouseDrafts[greenhouse.id] || initialGreenhouseDraft(greenhouse);
            const coolingRequested = listOrEmpty(greenhouse.states)
              .some((state) => state.scenario_type === 'BOX_CLIMATE' && state.ac_request_active);
            return (
              <article
                className={`farm-zone-editor ${greenhouse.enabled ? '' : 'is-disabled'}`}
                key={greenhouse.id}
              >
                <header className="farm-zone-editor__header">
                  <h2>{greenhouse.name}</h2>
                  <div className="farm-zone-editor__status">
                    {coolingRequested ? (
                      <span className="status-chip is-warning">
                        {translateApp('Требуется охлаждение')}
                      </span>
                    ) : null}
                    <span className={greenhouse.enabled ? 'status-chip is-online' : 'status-chip'}>
                      {greenhouse.enabled ? translateApp('Активна') : translateApp('Выключена')}
                    </span>
                  </div>
                </header>

                <SlotEditor
                  scope={greenhouse}
                  scopeType="BOX"
                  roles={FARM_SLOT_ROLES}
                  draft={draft}
                  catalog={catalog}
                  occupancy={occupancy}
                  onChange={(patch) => patchGreenhouseDraft(greenhouse.id, patch)}
                  onSave={() => saveSlots(
                    greenhouse,
                    'BOX',
                    draft,
                    (slots, reassign) => replaceGreenhouseSlots(
                      greenhouse.id,
                      slots,
                      reassign,
                    ),
                  )}
                  isSaving={busy === actionKey(greenhouse.id, 'BOX:slots')}
                />

                <section className="farm-zone-editor__section">
                  <div className="farm-zone-editor__section-heading">
                    <div>
                      <h3>{translateApp('Растения')}</h3>
                      <p>{translateApp('Растение может находиться только в одной теплице или оставаться без размещения.')}</p>
                    </div>
                    <Button
                      size="sm"
                      onClick={() => savePlants(greenhouse)}
                      isLoading={busy === actionKey(greenhouse.id, 'plants')}
                    >
                      {translateApp('Сохранить растения')}
                    </Button>
                  </div>
                  {plants.length === 0 ? (
                    <p className="farm-zone-editor__empty">
                      {translateApp('Сначала добавьте растение на странице «Растения».')}
                    </p>
                  ) : (
                    <div className="farm-zone-plants">
                      {plants.map((plant) => {
                        const plantDraft = draft.plants[plant.id]
                          || { selected: false, rate_ml_per_hour: '' };
                        const currentlyInThisGreenhouse = listOrEmpty(greenhouse.plants)
                          .some((itemPlant) => itemPlant.id === plant.id);
                        const occupiedGreenhouse = allGreenhouses.find((item) => (
                          item.id !== greenhouse.id
                          && listOrEmpty(item.plants).some((itemPlant) => itemPlant.id === plant.id)
                        ));
                        return (
                          <div
                            className={`farm-zone-plant ${plantDraft.selected ? 'is-selected' : ''}`}
                            key={plant.id}
                          >
                            <label>
                              <input
                                type="checkbox"
                                checked={plantDraft.selected}
                                onChange={(event) => patchGreenhouseDraft(greenhouse.id, {
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
                                <small>
                                  {occupiedGreenhouse
                                    ? `${translateApp('Сейчас')}: ${occupiedGreenhouse.name}`
                                    : (
                                      currentlyInThisGreenhouse
                                        ? translateApp('В этой теплице')
                                        : translateApp('Без теплицы')
                                    )}
                                </small>
                              </span>
                            </label>
                            <label>
                              <span>{translateApp('Скорость, мл/ч')}</span>
                              <input
                                type="number"
                                min="1"
                                step="1"
                                disabled={!plantDraft.selected}
                                value={plantDraft.rate_ml_per_hour}
                                onChange={(event) => patchGreenhouseDraft(greenhouse.id, {
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
                    <h3>{translateApp('Доступные сценарии')}</h3>
                    <Button
                      size="sm"
                      onClick={() => saveScenarios(greenhouse)}
                      isLoading={busy === actionKey(greenhouse.id, 'scenarios')}
                    >
                      {translateApp('Сохранить сценарии')}
                    </Button>
                  </div>
                  <div className="farm-scenarios">
                    {FARM_SCENARIO_TYPES.map((scenarioType) => {
                      const scenario = draft.scenarios[scenarioType];
                      const readiness = readinessFor(greenhouse, scenarioType);
                      const enableBlocked = !readiness.ready && !scenario.enabled;
                      return (
                        <div
                          className={`farm-scenario ${readiness.ready ? 'is-ready' : 'is-unready'}`}
                          key={scenarioType}
                        >
                          <div className="farm-scenario__header">
                            <label>
                              <input
                                type="checkbox"
                                checked={scenario.enabled}
                                disabled={enableBlocked}
                                onChange={(event) => patchGreenhouseDraft(greenhouse.id, {
                                  scenarios: {
                                    ...draft.scenarios,
                                    [scenarioType]: {
                                      ...scenario,
                                      enabled: event.target.checked,
                                    },
                                  },
                                })}
                              />
                              <strong>
                                {translateApp(SCENARIO_LABELS[scenarioType] || scenarioType)}
                              </strong>
                            </label>
                            <span>
                              {readiness.ready ? translateApp('Готово') : readiness.reason}
                            </span>
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
                                    patchGreenhouseDraft(greenhouse.id, {
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
