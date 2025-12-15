import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../../features/auth/AuthContext';
import { isSessionExpiredError } from '../../api/client';
import { formatDateKeyYYYYMMDD, formatTimeHHMM } from '../../utils/formatters';
import {
  DEFAULT_PLANT_TYPE_ID,
  getPlantTypeOptions,
  getStageOptionsForType,
  getStagesForType,
  normalizePlantTypeId,
} from '../../domain/plants';
import {
  createPlant,
  updatePlant,
  deletePlant,
  createPlantGroup,
  updatePlantGroup,
  deletePlantGroup,
} from '../../api/plants';
import { assignDeviceToPlant, unassignDeviceFromPlant } from '../../api/devices';
import DeviceCard from '../devices/DeviceCard';
import FormField from '../ui/FormField';
import Modal from '../ui/Modal';
import Button from '../ui/Button';
import './PlantEditDialog.css';

// Translitem: PlantEditDialog - dialog CRUD rastenija (polya, gruppy, privyazki ustrojstv); roditel delat refetch cherez onSaved().
function PlantEditDialog({
  isOpen,
  mode,
  plant,
  plants,
  plantGroups,
  devices,
  onClose,
  onSaved,
}) {
  const { token } = useAuth();
  const [localPlant, setLocalPlant] = useState({
    name: '',
    plant_type: DEFAULT_PLANT_TYPE_ID,
    strain: '',
    growth_stage: '',
    planted_at: '',
    plant_group_id: null,
    devices: [],
  });
  const [localGroups, setLocalGroups] = useState([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState('');
  const [error, setError] = useState(null);
  const [isSaving, setIsSaving] = useState(false);

  const plantTypeOptions = useMemo(() => getPlantTypeOptions('ru'), []);
  const selectedTypeId = useMemo(() => normalizePlantTypeId(localPlant.plant_type), [localPlant.plant_type]);
  const stageOptions = useMemo(() => getStageOptionsForType('ru', selectedTypeId), [selectedTypeId]);

  const assignedDevices = useMemo(() => Array.isArray(localPlant.devices) ? localPlant.devices : [], [localPlant.devices]);

  const freeDevices = useMemo(() => {
    const assignedIds = new Set((assignedDevices || []).map((d) => d.id));
    const currentPlantId = plant?.id;
    return (devices || []).filter((device) => {
      if (assignedIds.has(device.id)) return false;
      if (!Array.isArray(device.plant_ids) || device.plant_ids.length === 0) return true;
      if (currentPlantId && device.plant_ids.includes(currentPlantId)) return false;
      return device.plant_ids.length === 0;
    });
  }, [assignedDevices, devices, plant?.id]);

  const toLocalDateTimeInput = (isoValue) => {
    if (!isoValue) return '';
    // Translitem: datetime iz backenda privodim k UI timezone (Moskva) i formatu input[type=datetime-local].
    const dateKey = formatDateKeyYYYYMMDD(isoValue);
    const time = formatTimeHHMM(isoValue);
    if (!dateKey || !time) return '';
    return `${dateKey}T${time}`;
  };

  const toIsoString = (localValue) => {
    if (!localValue) return null;
    const date = new Date(localValue);
    if (Number.isNaN(date.getTime())) return null;
    return date.toISOString();
  };

  useEffect(() => {
    if (!isOpen) return;
    setError(null);
    setIsSaving(false);
    setSelectedDeviceId('');
    setLocalGroups(Array.isArray(plantGroups) ? plantGroups : []);
    if (mode === 'edit' && plant) {
      setLocalPlant({
        name: plant.name || '',
        plant_type: normalizePlantTypeId(plant.plant_type || DEFAULT_PLANT_TYPE_ID),
        strain: plant.strain || '',
        growth_stage: plant.growth_stage || '',
        planted_at: toLocalDateTimeInput(plant.planted_at),
        plant_group_id: plant.plant_group?.id ?? plant.plant_group_id ?? null,
        devices: Array.isArray(plant.devices) ? plant.devices : [],
      });
    } else {
      setLocalPlant({
        name: '',
        plant_type: DEFAULT_PLANT_TYPE_ID,
        strain: '',
        growth_stage: '',
        planted_at: '',
        plant_group_id: null,
        devices: [],
      });
    }
  }, [isOpen, mode, plant, plantGroups]);

  if (!isOpen) {
    return null;
  }

  const handleGroupChange = (value) => {
    setLocalPlant((prev) => ({
      ...prev,
      plant_group_id: value === '' || value === null ? null : Number(value),
    }));
  };

  const handlePlantTypeChange = (value) => {
    const nextTypeId = normalizePlantTypeId(value);
    setLocalPlant((prev) => {
      const currentStage = prev.growth_stage ? String(prev.growth_stage).trim() : '';
      const allowed = new Set(getStagesForType(nextTypeId));
      const nextStage = currentStage && allowed.has(currentStage) ? currentStage : '';
      return {
        ...prev,
        plant_type: nextTypeId,
        growth_stage: nextStage,
      };
    });
  };

  // Translitem: operacii nad gruppami vnutri dialoga (roditel potom refetch cherez onSaved()).
  const handleCreateGroup = async () => {
    const name = window.prompt('Введите название новой группы');
    if (!name || !name.trim()) return;
    try {
      const created = await createPlantGroup(token, { name: name.trim() });
      setLocalGroups((prev) => [...prev, created]);
      setLocalPlant((prev) => ({ ...prev, plant_group_id: created.id }));
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось создать группу');
    }
  };

  const handleRenameGroup = async () => {
    const groupId = localPlant.plant_group_id;
    if (!groupId) {
      window.alert('Выберите группу для переименования');
      return;
    }
    const current = localGroups.find((g) => g.id === groupId);
    const newName = window.prompt('Новое название группы', current?.name || '');
    if (!newName || !newName.trim()) return;
    try {
      const updated = await updatePlantGroup(token, groupId, { name: newName.trim() });
      setLocalGroups((prev) => prev.map((g) => (g.id === groupId ? updated : g)));
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось переименовать группу');
    }
  };

  const handleDeleteGroup = async () => {
    const groupId = localPlant.plant_group_id;
    if (!groupId) return;
    const confirmed = window.confirm('Удалить группу?');
    if (!confirmed) return;
    try {
      await deletePlantGroup(token, groupId);
      setLocalGroups((prev) => prev.filter((g) => g.id !== groupId));
      setLocalPlant((prev) => ({ ...prev, plant_group_id: null }));
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось удалить группу');
    }
  };

  const handleAttachDevice = async () => {
    if (!plant?.id) return;
    const deviceId = selectedDeviceId ? Number(selectedDeviceId) : null;
    if (!deviceId) return;
    try {
      await assignDeviceToPlant(deviceId, plant.id, token);
      const found = devices.find((d) => d.id === deviceId);
      const newDevice = found || { id: deviceId, device_id: deviceId };
      setLocalPlant((prev) => ({
        ...prev,
        devices: [...(prev.devices || []), newDevice],
      }));
      setSelectedDeviceId('');
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось привязать устройство');
    }
  };

  const handleDetachDevice = async (deviceId) => {
    if (!plant?.id) return;
    try {
      await unassignDeviceFromPlant(deviceId, plant.id, token);
      setLocalPlant((prev) => ({
        ...prev,
        devices: (prev.devices || []).filter((d) => d.id !== deviceId),
      }));
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось отвязать устройство');
    }
  };

  const handleSave = async () => {
    if (!localPlant.name.trim()) {
      setError('Укажите название растения');
      return;
    }
    setIsSaving(true);
    setError(null);
    const payload = {
      name: localPlant.name.trim(),
      plant_type: localPlant.plant_type || null,
      strain: localPlant.strain || null,
      growth_stage: localPlant.growth_stage || null,
      plant_group_id: localPlant.plant_group_id ?? null,
    };
    const plantedIso = toIsoString(localPlant.planted_at);
    if (plantedIso) {
      payload.planted_at = plantedIso;
    }
    try {
      if (mode === 'create') {
        await createPlant(token, payload);
      } else if (mode === 'edit' && plant?.id) {
        await updatePlant(token, plant.id, payload);
      }
      onSaved?.();
      onClose?.();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось сохранить растение');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeletePlant = async () => {
    if (!plant?.id) return;
    const confirmed = window.confirm('Точно удалить растение?');
    if (!confirmed) return;
    setIsSaving(true);
    setError(null);
    try {
      await deletePlant(token, plant.id);
      onSaved?.();
      onClose?.();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось удалить растение');
    } finally {
      setIsSaving(false);
    }
  };

  const title = mode === 'create' ? 'Новое растение' : 'Редактировать растение';

  const footer = (
    <div className="plant-dialog__footer">
      {mode === 'edit' && plant?.id && (
        <Button variant="danger" onClick={handleDeletePlant} disabled={isSaving}>
          Удалить
        </Button>
      )}
      <div className="plant-dialog__footer-actions">
        <Button variant="secondary" onClick={onClose} disabled={isSaving}>
          Отмена
        </Button>
        <Button variant="primary" onClick={handleSave} disabled={isSaving}>
          {isSaving ? 'Сохранение...' : 'Сохранить'}
        </Button>
      </div>
    </div>
  );

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title} disableOverlayClose footer={footer}>
      {error && <div className="plant-dialog__error">{error}</div>}

      <div className="plant-dialog__body">
        <FormField label="Название" htmlFor="plant-name" className="plant-dialog__field">
          <input
            id="plant-name"
            value={localPlant.name}
            onChange={(e) => setLocalPlant((prev) => ({ ...prev, name: e.target.value }))}
            placeholder="Basil"
          />
        </FormField>

        <FormField label="Тип растения" htmlFor="plant-type" className="plant-dialog__field">
          <select
            id="plant-type"
            value={selectedTypeId}
            onChange={(e) => handlePlantTypeChange(e.target.value)}
          >
            {plantTypeOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </FormField>

        <FormField label="Сорт" htmlFor="plant-strain" className="plant-dialog__field">
          <input
            id="plant-strain"
            value={localPlant.strain || ''}
            onChange={(e) => setLocalPlant((prev) => ({ ...prev, strain: e.target.value }))}
          />
        </FormField>

        <FormField label="Стадия роста" htmlFor="growth-stage" className="plant-dialog__field">
          <select
            id="growth-stage"
            value={localPlant.growth_stage || ''}
            onChange={(e) => setLocalPlant((prev) => ({ ...prev, growth_stage: e.target.value || '' }))}
          >
            {stageOptions.map((opt) => (
              <option key={opt.value || 'auto'} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </FormField>

        <FormField label="Дата посадки" htmlFor="planted-at" className="plant-dialog__field">
          <input
            id="planted-at"
            type="datetime-local"
            value={localPlant.planted_at || ''}
            onChange={(e) => setLocalPlant((prev) => ({ ...prev, planted_at: e.target.value }))}
          />
        </FormField>

        <div className="plant-dialog__group-row">
          <FormField label="Группа" htmlFor="plant-group" className="plant-dialog__field">
            <select
              id="plant-group"
              value={localPlant.plant_group_id ?? ''}
              onChange={(e) => handleGroupChange(e.target.value)}
            >
              <option value="">Без группы</option>
              {localGroups.map((group) => (
                <option key={group.id} value={group.id}>{group.name}</option>
              ))}
            </select>
          </FormField>
          <div className="plant-dialog__group-actions">
            <button type="button" onClick={handleCreateGroup}>Создать</button>
            <button type="button" onClick={handleRenameGroup}>Переименовать</button>
            <button type="button" onClick={handleDeleteGroup}>Удалить</button>
          </div>
        </div>
      </div>

      <div className="plant-dialog__section">
        <div className="plant-dialog__section-title">Устройства</div>
        {mode === 'create' && (
          <div className="plant-dialog__hint">Привязать устройства можно после сохранения растения.</div>
        )}
        {mode === 'edit' && (
          <>
            {assignedDevices.length === 0 && (
              <div className="plant-dialog__hint">Нет привязанных устройств</div>
            )}
            {assignedDevices.length > 0 && (
              <div className="plant-dialog__devices">
                {assignedDevices.map((device) => (
                  <div key={device.id} className="plant-dialog__device-row">
                    <DeviceCard device={device} variant="plant" />
                    <button
                      type="button"
                      className="plant-dialog__unlink"
                      onClick={() => handleDetachDevice(device.id)}
                    >
                      Отвязать
                    </button>
                  </div>
                ))}
              </div>
            )}
            <div className="plant-dialog__attach">
              <select
                value={selectedDeviceId}
                onChange={(e) => setSelectedDeviceId(e.target.value)}
              >
                <option value="">Выберите устройство</option>
                {freeDevices.map((device) => (
                  <option key={device.id} value={device.id}>
                    {device.name || device.device_id || `Device ${device.id}`}
                  </option>
                ))}
              </select>
              <button type="button" onClick={handleAttachDevice}>
                Привязать
              </button>
            </div>
          </>
        )}
      </div>
    </Modal>
  );
}

export default PlantEditDialog;
