import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AirVent, ChevronDown, ChevronRight, Cpu, Droplets, Fan, Lightbulb, Plus, Sprout, Thermometer, Waves } from 'lucide-react';
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
import Modal from '../../components/ui/Modal';
import useCompactLayout from '../../components/layout/useCompactLayout';
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
const SENSOR_ROLES = ['AIR_TEMPERATURE_SENSOR', 'AIR_HUMIDITY_SENSOR', 'SOIL_MOISTURE_SENSOR'];
const SLOT_ICONS = {
  AC_SWITCH: AirVent, AIR_TEMPERATURE_SENSOR: Thermometer, AIR_HUMIDITY_SENSOR: Waves,
  SOIL_MOISTURE_SENSOR: Sprout, WATER_PUMP: Droplets, LIGHT_SWITCH: Lightbulb,
  EXHAUST_SWITCH: Fan, LEAK_SENSOR: Waves,
};
const slotsChanged = (scope, draft) => (
  JSON.stringify([...listOrEmpty(draft?.visibleRoles)].sort()) !== JSON.stringify(listOrEmpty(scope.slots).map((slot) => slot.role).sort())
  || listOrEmpty(draft?.visibleRoles).some((role) => (draft.slots?.[role] || '') !== bindingOptionValue(slotForRole(scope, role)))
);

function compactSlotGroups(scope, draft, catalog) {
  const groups = [];
  listOrEmpty(draft?.visibleRoles).forEach((role) => {
    const value = draft.slots?.[role] || '';
    const binding = slotForRole(scope, role);
    const parsed = parseOptionValue(value);
    const native = listOrEmpty(catalog.native_devices).find((device) => (
      parsed?.source_type === 'NATIVE_SENSOR'
        ? listOrEmpty(device.sensors).some((sensor) => sensor.id === parsed.native_sensor_id)
        : parsed?.source_type === 'NATIVE_PUMP'
          && listOrEmpty(device.pumps).some((pump) => pump.id === parsed.native_pump_id)
    ));
    const zigbee = parsed?.source_type === 'ZIGBEE_DEVICE'
      ? listOrEmpty(catalog.zigbee_devices).find((device) => (
        device.coordinator_id === parsed.zigbee_coordinator_id
        && device.ieee_address?.toLowerCase() === parsed.zigbee_ieee_address?.toLowerCase()
      )) : null;
    const deviceKey = native ? `native:${native.id}` : (zigbee ? `zigbee:${zigbee.coordinator_id}:${zigbee.ieee_address}` : null);
    const isSaved = value === bindingOptionValue(binding);
    const warning = isSaved && (binding?.connection_status === 'warning' || binding?.ready === false);
    const label = value ? (native?.name || native?.device_id || zigbee?.friendly_name || binding?.label || translateApp('Устройство')) : translateApp('Не назначено');
    const group = SENSOR_ROLES.includes(role) && deviceKey
      ? groups.find((item) => item.deviceKey === deviceKey && SENSOR_ROLES.includes(item.roles[0])) : null;
    if (group) {
      group.roles.push(role);
      group.warning ||= warning;
      group.dirty ||= !isSaved;
    } else {
      groups.push({ roles: [role], label, deviceKey, warning, dirty: !isSaved });
    }
  });
  return groups;
}

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
  compact = false,
  error,
}) {
  const [editing, setEditing] = useState(null);
  const visibleRoles = listOrEmpty(draft?.visibleRoles);
  const availableRoles = roles.filter((role) => !visibleRoles.includes(role));
  const editedRoles = compact && Array.isArray(editing)
    ? visibleRoles.filter((role) => editing.includes(role)) : visibleRoles;
  const dirty = slotsChanged(scope, draft);

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

  const editor = (
    <section className="farm-zone-editor__section">
      <div className="farm-zone-editor__section-heading">
        <h3>{translateApp('Слоты устройств')}</h3>
        <Button size="sm" onClick={async () => { if (await onSave()) setEditing(null); }} isLoading={isSaving}>
          {translateApp('Сохранить слоты')}
        </Button>
      </div>

      {editedRoles.length > 0 ? (
        <div className="farm-slots">
          {editedRoles.map((role) => {
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

  if (!compact) return editor;
  return (
    <div className={`farm-compact-slots ${scopeType === 'ROOM' ? 'farm-compact-slots--farm' : ''}`}>
      {compactSlotGroups(scope, draft, catalog).map((group) => {
        const Icon = group.roles.length > 1 ? Cpu : SLOT_ICONS[group.roles[0]] || Cpu;
        return <button type="button" className={`farm-compact-slot ${group.warning ? 'is-warning' : ''}`}
          key={group.roles[0]} onClick={() => setEditing(group.roles)}>
          <Icon size={18} aria-hidden="true" />
          <span><strong>{scopeType === 'ROOM' ? translateApp('Кондиционер фермы') : group.roles.length > 1 ? translateApp('Датчики') : translateApp(SLOT_ROLE_LABELS[group.roles[0]] || group.roles[0])}</strong>{' '}
            <small>{group.label}{group.roles.length > 1 ? ` · ${group.roles.length}` : ''}</small></span>
          {group.warning ? <span className="farm-compact-slot__state">{translateApp('Проверьте')}</span> : null}
          {group.dirty ? <span className="farm-compact-slot__state">{translateApp('Не сохранено')}</span> : null}
          <ChevronRight size={16} aria-hidden="true" />
        </button>;
      })}
      {availableRoles.length > 0 ? <button type="button" className="farm-compact-add" onClick={() => setEditing('all')} aria-label={translateApp('Добавить слот')}>
        <Plus size={18} aria-hidden="true" /><span>{translateApp('Добавить слот')}</span>
      </button> : null}
      {dirty ? <div className="farm-compact-slots__actions"><Button size="sm" onClick={onSave} isLoading={isSaving}>{translateApp('Сохранить слоты')}</Button></div> : null}
      <Modal isOpen={editing !== null} title={`${scope.name} · ${translateApp('Слоты устройств')}`} presentation="sheet"
        onClose={() => setEditing(null)}>
        {error ? <p className="farm-slot-editor__error" role="alert">{error}</p> : null}
        {editor}
      </Modal>
    </div>
  );
}

function FarmConstructor() {
  const compact = useCompactLayout();
  const [expandedByFarm, setExpandedByFarm] = useState({});
  const [plantListId, setPlantListId] = useState(null);
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
      return true;
    } catch (requestError) {
      setError(requestError?.message || translateApp('Не удалось сохранить изменения'));
      return false;
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
    return runAction(
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
      setPlantListId(null);
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
        title={translateApp(compact ? 'Ферма' : 'Конструктор фермы')}
        right={(
          <Link className="gh-btn gh-btn--secondary gh-btn--md" to={compact ? '/app/settings/zones/' : '/app/settings/devices/'}>
            {translateApp(compact ? 'Структура' : 'Все устройства')}
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
            aria-label={translateApp('Ферма')}
            value={selectedFarmId}
            onChange={(event) => setSelectedFarmId(event.target.value)}
          >
            {farms.map((farm) => (
              <option key={farm.id} value={farm.id}>{farm.name}</option>
            ))}
          </select>
          {compact && selectedFarm.enabled === false ? <small className="farm-constructor__farm-disabled">{translateApp('Ферма выключена')}</small> : null}
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
          key={selectedFarm.id}
          compact={compact}
          error={error}
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
            const expandedId = Object.hasOwn(expandedByFarm, selectedFarm.id)
              ? expandedByFarm[selectedFarm.id] : greenhouses[0]?.id;
            const expanded = !compact || expandedId === greenhouse.id;
            const assignedCount = listOrEmpty(draft.visibleRoles).filter((role) => draft.slots?.[role]).length;
            const coolingRequested = listOrEmpty(greenhouse.states)
              .some((state) => state.scenario_type === 'BOX_CLIMATE' && state.ac_request_active);
            const plantsEditor = (
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
            );
            return (
              <article
                className={`farm-zone-editor ${greenhouse.enabled ? '' : 'is-disabled'} ${draft.visibleRoles.length < FARM_SLOT_ROLES.length ? 'has-available-slots' : ''}`}
                key={greenhouse.id}
              >
                {compact ? <header className="farm-zone-editor__compact-header">
                  <h2><button type="button" aria-expanded={expanded} aria-controls={`farm-greenhouse-${greenhouse.id}`}
                    onClick={() => setExpandedByFarm((current) => ({ ...current, [selectedFarm.id]: expanded ? null : greenhouse.id }))}>
                    <span>{greenhouse.name}{' '}{!expanded ? <small>{slotsChanged(greenhouse, draft) ? `${translateApp('Не сохранено')} · ` : ''}{assignedCount} / {draft.visibleRoles.length} · {translateApp('Растения')}: {listOrEmpty(greenhouse.plants).length}</small> : null}</span>
                    {expanded ? <small>{assignedCount} / {draft.visibleRoles.length}</small> : null}
                    <ChevronDown size={18} aria-hidden="true" />
                  </button></h2>
                  {!greenhouse.enabled || coolingRequested ? <div className="farm-zone-editor__compact-status">
                    {!greenhouse.enabled ? <span>{translateApp('Выключена')}</span> : null}
                    {coolingRequested ? <span>{translateApp('Требуется охлаждение')}</span> : null}
                  </div> : null}
                </header> : <header className="farm-zone-editor__header">
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
                </header>}

                <div className="farm-zone-editor__body" id={`farm-greenhouse-${greenhouse.id}`} hidden={!expanded}>
                <SlotEditor
                  compact={compact}
                  error={error}
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

                {compact ? <>
                  <button type="button" className="farm-zone-plants-summary" onClick={() => setPlantListId(greenhouse.id)}>
                    <Sprout size={18} aria-hidden="true" /><span>{listOrEmpty(greenhouse.plants).map((plant) => plant.name).join(' · ') || translateApp('Растения не привязаны')}</span><ChevronRight size={16} aria-hidden="true" />
                  </button>
                  <Modal isOpen={plantListId === greenhouse.id} presentation="sheet" title={`${greenhouse.name} · ${translateApp('Растения')}`} onClose={() => setPlantListId(null)}>
                    {error ? <p role="alert" className="farm-slot-editor__error">{error}</p> : null}
                    {plantsEditor}
                    <Link to="/app/plants/">{translateApp('Растения')}</Link>
                  </Modal>
                </> : plantsEditor}
                </div>

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
