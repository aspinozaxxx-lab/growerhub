import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../../features/auth/AuthContext';
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
import './PlantEditDialog.css';

const STAGE_LABELS = {
  seed: 'Seed',
  seedling: 'Seedling',
  vegetative: 'Vegetative',
  preflower: 'Pre-flower',
  flowering: 'Flowering',
  ripening: 'Ripening',
  harvest_ready: 'Harvest ready',
};

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
    plant_type: '',
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

  const stageOptions = useMemo(() => [
    { value: '', label: 'Avto po vozrastu' },
    ...Object.entries(STAGE_LABELS).map(([value, label]) => ({ value, label })),
  ], []);

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
    const date = new Date(isoValue);
    if (Number.isNaN(date.getTime())) return '';
    const pad = (v) => String(v).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
        plant_type: plant.plant_type || '',
        strain: plant.strain || '',
        growth_stage: plant.growth_stage || '',
        planted_at: toLocalDateTimeInput(plant.planted_at),
        plant_group_id: plant.plant_group?.id ?? plant.plant_group_id ?? null,
        devices: Array.isArray(plant.devices) ? plant.devices : [],
      });
    } else {
      setLocalPlant({
        name: '',
        plant_type: '',
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

  // Translitem: operacii nad gruppami vnutri dialoga (roditel potom refetch cherez onSaved()).
  const handleCreateGroup = async () => {
    const name = window.prompt('Vvedite nazvanie novoj gruppy');
    if (!name || !name.trim()) return;
    try {
      const created = await createPlantGroup(token, { name: name.trim() });
      setLocalGroups((prev) => [...prev, created]);
      setLocalPlant((prev) => ({ ...prev, plant_group_id: created.id }));
    } catch (err) {
      setError(err?.message || 'Ne udalos sozdat gruppu');
    }
  };

  const handleRenameGroup = async () => {
    const groupId = localPlant.plant_group_id;
    if (!groupId) {
      window.alert('Vyberite gruppu dlya pereimenovaniya');
      return;
    }
    const current = localGroups.find((g) => g.id === groupId);
    const newName = window.prompt('Novoe nazvanie gruppy', current?.name || '');
    if (!newName || !newName.trim()) return;
    try {
      const updated = await updatePlantGroup(token, groupId, { name: newName.trim() });
      setLocalGroups((prev) => prev.map((g) => (g.id === groupId ? updated : g)));
    } catch (err) {
      setError(err?.message || 'Ne udalos pereimenovat gruppu');
    }
  };

  const handleDeleteGroup = async () => {
    const groupId = localPlant.plant_group_id;
    if (!groupId) return;
    const confirmed = window.confirm('Udalit gruppu?');
    if (!confirmed) return;
    try {
      await deletePlantGroup(token, groupId);
      setLocalGroups((prev) => prev.filter((g) => g.id !== groupId));
      setLocalPlant((prev) => ({ ...prev, plant_group_id: null }));
    } catch (err) {
      setError(err?.message || 'Ne udalos udaliti gruppu');
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
      setError(err?.message || 'Ne udalos privyazat ustrojstvo');
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
      setError(err?.message || 'Ne udalos otvyazat ustrojstvo');
    }
  };

  const handleSave = async () => {
    if (!localPlant.name.trim()) {
      setError('Ukazhite nazvanie rastenija');
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
      setError(err?.message || 'Ne udalos sohranit rastenie');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeletePlant = async () => {
    if (!plant?.id) return;
    const confirmed = window.confirm('Tochno udalit rastenie?');
    if (!confirmed) return;
    setIsSaving(true);
    setError(null);
    try {
      await deletePlant(token, plant.id);
      onSaved?.();
      onClose?.();
    } catch (err) {
      setError(err?.message || 'Ne udalos udalit rastenie');
    } finally {
      setIsSaving(false);
    }
  };

  const title = mode === 'create' ? 'Novoe rastenie' : 'Redaktirovat\' rastenie';

  return (
    <div className="plant-dialog__overlay">
      <div className="plant-dialog">
        <div className="plant-dialog__header">
          <div className="plant-dialog__title">{title}</div>
          <button type="button" className="plant-dialog__close" onClick={onClose} aria-label="Zakryt">
            вњ•
          </button>
        </div>

        {error && <div className="plant-dialog__error">{error}</div>}

        <div className="plant-dialog__body">
          <label className="plant-dialog__field">
            <span className="plant-dialog__label">Nazvanie</span>
            <input
              value={localPlant.name}
              onChange={(e) => setLocalPlant((prev) => ({ ...prev, name: e.target.value }))}
              placeholder="Basil"
            />
          </label>

          <label className="plant-dialog__field">
            <span className="plant-dialog__label">Tip rastenija</span>
            <input
              value={localPlant.plant_type || ''}
              onChange={(e) => setLocalPlant((prev) => ({ ...prev, plant_type: e.target.value }))}
              placeholder="flowering"
            />
          </label>

          <label className="plant-dialog__field">
            <span className="plant-dialog__label">Strain</span>
            <input
              value={localPlant.strain || ''}
              onChange={(e) => setLocalPlant((prev) => ({ ...prev, strain: e.target.value }))}
              placeholder="Mint"
            />
          </label>

          <label className="plant-dialog__field">
            <span className="plant-dialog__label">Stadija rosta</span>
            <select
              value={localPlant.growth_stage || ''}
              onChange={(e) => setLocalPlant((prev) => ({ ...prev, growth_stage: e.target.value || '' }))}
            >
              {stageOptions.map((opt) => (
                <option key={opt.value || 'auto'} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </label>

          <label className="plant-dialog__field">
            <span className="plant-dialog__label">Data posadki</span>
            <input
              type="datetime-local"
              value={localPlant.planted_at || ''}
              onChange={(e) => setLocalPlant((prev) => ({ ...prev, planted_at: e.target.value }))}
            />
          </label>

          <div className="plant-dialog__field">
            <span className="plant-dialog__label">Gruppa</span>
            <div className="plant-dialog__group-row">
              <select
                value={localPlant.plant_group_id ?? ''}
                onChange={(e) => handleGroupChange(e.target.value)}
              >
                <option value="">Bez gruppy</option>
                {localGroups.map((group) => (
                  <option key={group.id} value={group.id}>{group.name}</option>
                ))}
              </select>
              <div className="plant-dialog__group-actions">
                <button type="button" onClick={handleCreateGroup}>Sozdat'</button>
                <button type="button" onClick={handleRenameGroup}>Pereimenovat'</button>
                <button type="button" onClick={handleDeleteGroup}>Udalit'</button>
              </div>
            </div>
          </div>

          <div className="plant-dialog__section">
            <div className="plant-dialog__section-title">Ustrojstva</div>
            {mode === 'create' && (
              <div className="plant-dialog__hint">Privyazat ustrojstva mozhno posle sohraneniya rastenija.</div>
            )}
            {mode === 'edit' && (
              <>
                {assignedDevices.length === 0 && (
                  <div className="plant-dialog__hint">Net privyazannyh ustrojstv</div>
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
                          Otvjazat'
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
                    <option value="">Vyberite ustrojstvo</option>
                    {freeDevices.map((device) => (
                      <option key={device.id} value={device.id}>
                        {device.name || device.device_id || `Device ${device.id}`}
                      </option>
                    ))}
                  </select>
                  <button type="button" onClick={handleAttachDevice}>
                    Privyazat'
                  </button>
                </div>
              </>
            )}
          </div>
        </div>

        <div className="plant-dialog__footer">
          {mode === 'edit' && plant?.id && (
            <button
              type="button"
              className="plant-dialog__btn plant-dialog__btn--danger"
              onClick={handleDeletePlant}
              disabled={isSaving}
            >
              Udalit'
            </button>
          )}
          <div className="plant-dialog__footer-actions">
            <button type="button" className="plant-dialog__btn" onClick={onClose} disabled={isSaving}>
              Otmena
            </button>
            <button
              type="button"
              className="plant-dialog__btn plant-dialog__btn--primary"
              onClick={handleSave}
              disabled={isSaving}
            >
              {isSaving ? 'Sohranenie...' : 'Sohranit\''}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default PlantEditDialog;

