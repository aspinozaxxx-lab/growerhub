import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  fetchFarmsOverview,
  replaceGreenhouseSlots,
  replaceUserFarmSlots,
  updateGreenhousePlantWateringRate,
} from '../../api/selfService';
import { fetchPlant } from '../../api/plants';
import PlantEditDialog from '../../components/plants/PlantEditDialog';
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
  FARM_SLOT_ROLES,
  SLOT_ROLE_LABELS,
  buildSlotOccupancy,
  findSlotConflicts,
  listOrEmpty,
  overviewFarms,
  slotForRole,
} from './farmModel';
import { translateApp } from '../../locales/i18n';
import './FarmConstructor.css';

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
      rate_ml_per_hour: plant.rate_ml_per_hour ?? '',
    },
  ]));
  return {
    ...initialSlotDraft(greenhouse),
    plants,
  };
}

function initialFarmDraft(farm) {
  return initialSlotDraft(farm);
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
              ? (
                binding?.connection_status === 'ok'
                  ? translateApp('на связи')
                  : (binding?.connection_message || translateApp('статус неизвестен'))
              )
              : (
                selectedOption?.connectionStatus === 'online'
                  ? translateApp('на связи')
                  : (selectedOption?.connectionStatus === 'offline'
                    ? translateApp('нет связи')
                    : translateApp('статус неизвестен'))
              );
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
  const [selectedPlant, setSelectedPlant] = useState(null);
  const [plantDialogOpen, setPlantDialogOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [rateStatuses, setRateStatuses] = useState({});
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
      initialFarmDraft(farm),
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
  const plantZones = farms.flatMap((farm) => listOrEmpty(farm.greenhouses)
    .map((greenhouse) => ({
      ...greenhouse,
      name: `${farm.name} · ${greenhouse.name}`,
    })));
  const occupancyScopes = useMemo(() => farms.flatMap((farm) => [
    { ...farm, name: farm.name, scope_type: 'ROOM' },
    ...listOrEmpty(farm.greenhouses).map((greenhouse) => ({
      ...greenhouse,
      name: `${farm.name} · ${greenhouse.name}`,
      scope_type: 'BOX',
    })),
  ]), [farms]);
  const occupancy = useMemo(() => buildSlotOccupancy(occupancyScopes), [occupancyScopes]);

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

  const savePlantRate = async (greenhouse, plant) => {
    const key = actionKey(greenhouse.id, `plant:${plant.id}`);
    const value = greenhouseDrafts[greenhouse.id]?.plants?.[plant.id]?.rate_ml_per_hour ?? '';
    const rate = value === '' ? null : Number(value);
    if (rate !== null && (!Number.isInteger(rate) || rate <= 0)) {
      patchGreenhouseDraft(greenhouse.id, {
        plants: {
          ...(greenhouseDrafts[greenhouse.id]?.plants || {}),
          [plant.id]: { rate_ml_per_hour: plant.rate_ml_per_hour ?? '' },
        },
      });
      setRateStatuses((current) => ({ ...current, [key]: 'error' }));
      setError(translateApp('Скорость полива должна быть положительным целым числом'));
      return;
    }
    if (rate === (plant.rate_ml_per_hour ?? null)) return;
    setBusy(key);
    setError('');
    setNotice('');
    setRateStatuses((current) => ({ ...current, [key]: 'saving' }));
    try {
      applyOverview(await updateGreenhousePlantWateringRate(greenhouse.id, plant.id, rate));
      setRateStatuses((current) => ({ ...current, [key]: 'saved' }));
    } catch (requestError) {
      patchGreenhouseDraft(greenhouse.id, {
        plants: {
          ...(greenhouseDrafts[greenhouse.id]?.plants || {}),
          [plant.id]: { rate_ml_per_hour: plant.rate_ml_per_hour ?? '' },
        },
      });
      setRateStatuses((current) => ({ ...current, [key]: 'error' }));
      setError(requestError?.message || translateApp('Не удалось сохранить скорость полива'));
    } finally {
      setBusy('');
    }
  };

  const openPlantEditor = async (plant) => {
    const key = actionKey(plant.id, 'plant:open');
    setBusy(key);
    setError('');
    try {
      setSelectedPlant(await fetchPlant(null, plant.id));
      setPlantDialogOpen(true);
    } catch (requestError) {
      setError(requestError?.message || translateApp('Не удалось загрузить растение'));
    } finally {
      setBusy('');
    }
  };

  const handlePlantSaved = async () => {
    setPlantDialogOpen(false);
    setSelectedPlant(null);
    setError('');
    try {
      applyOverview(await fetchFarmsOverview());
    } catch (requestError) {
      setError(requestError?.message || translateApp('Не удалось обновить конструктор'));
    }
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

  const farmDraft = farmDrafts[selectedFarm?.id] || initialFarmDraft(selectedFarm);

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
        {translateApp('Назначьте оборудование и разместите растения в теплицах.')} {' '}
        <Link to="/app/automations/">{translateApp('Настроить автоматизации')}</Link>
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
                    <h3>{translateApp('Растения')}</h3>
                  </div>
                  {listOrEmpty(greenhouse.plants).length === 0 ? (
                    <p className="farm-zone-editor__empty">
                      {translateApp('Растения не привязаны')}
                    </p>
                  ) : (
                    <div className="farm-zone-plants">
                      {listOrEmpty(greenhouse.plants).map((plant) => {
                        const plantDraft = draft.plants[plant.id]
                          || { rate_ml_per_hour: plant.rate_ml_per_hour ?? '' };
                        const plantBusy = busy === actionKey(plant.id, 'plant:open');
                        const rateKey = actionKey(greenhouse.id, `plant:${plant.id}`);
                        const rateBusy = busy === rateKey;
                        const rateStatus = rateStatuses[rateKey];
                        return (
                          <div className="farm-zone-plant" key={plant.id}>
                            <button
                              type="button"
                              className="farm-zone-plant__name"
                              onClick={() => openPlantEditor(plant)}
                              disabled={plantBusy}
                            >
                              {plant.name || translateApp('Растение без названия')}
                            </button>
                            <label className="farm-zone-plant__rate">
                              <input
                                type="number"
                                min="1"
                                step="1"
                                aria-label={translateApp('Скорость полива для {{value1}}, мл/ч', {
                                  value1: plant.name || translateApp('Растение без названия'),
                                })}
                                disabled={rateBusy}
                                value={plantDraft.rate_ml_per_hour}
                                onChange={(event) => {
                                  setRateStatuses((current) => ({
                                    ...current,
                                    [rateKey]: '',
                                  }));
                                  patchGreenhouseDraft(greenhouse.id, {
                                    plants: {
                                      ...draft.plants,
                                      [plant.id]: {
                                        ...plantDraft,
                                        rate_ml_per_hour: event.target.value,
                                      },
                                    },
                                  });
                                }}
                                onBlur={() => savePlantRate(greenhouse, plant)}
                                onKeyDown={(event) => {
                                  if (event.key === 'Enter') event.currentTarget.blur();
                                }}
                              />
                              <span>{translateApp('мл/ч')}</span>
                            </label>
                            {rateStatus ? (
                              <span
                                className={`farm-zone-plant__save-state is-${rateStatus}`}
                                role="status"
                              >
                                {rateStatus === 'saving'
                                  ? translateApp('Сохраняем...')
                                  : (rateStatus === 'saved'
                                    ? translateApp('Сохранено')
                                    : translateApp('Не сохранено'))}
                              </span>
                            ) : null}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </section>

              </article>
            );
          })}
        </div>
      )}
      {plantDialogOpen ? (
        <PlantEditDialog
          isOpen
          mode="edit"
          plant={selectedPlant}
          zones={plantZones}
          onClose={() => {
            setPlantDialogOpen(false);
            setSelectedPlant(null);
          }}
          onSaved={handlePlantSaved}
        />
      ) : null}
    </div>
  );
}

export default FarmConstructor;
