import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../../features/auth/AuthContext';
import { isSessionExpiredError } from '../../api/client';
import {
  formatDateTimeInput,
  zonedDateTimeInputToUtc,
} from '../../utils/formatters';
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
} from '../../api/plants';
import FormField from '../ui/FormField';
import Modal from '../ui/Modal';
import Button from '../ui/Button';
import './PlantEditDialog.css';
import { getCurrentLocale, translateApp } from '../../locales/i18n';

// Translitem: PlantEditDialog - dialog CRUD rastenija s atomarnym vyborom teplicy.
function PlantEditDialog({
  isOpen,
  mode,
  plant,
  zones,
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
    zone_id: null,
  });
  const [error, setError] = useState(null);
  const [isSaving, setIsSaving] = useState(false);

  const locale = getCurrentLocale();
  const plantTypeOptions = useMemo(() => getPlantTypeOptions(locale), [locale]);
  const selectedTypeId = useMemo(() => normalizePlantTypeId(localPlant.plant_type), [localPlant.plant_type]);
  const stageOptions = useMemo(
    () => getStageOptionsForType(locale, selectedTypeId),
    [locale, selectedTypeId],
  );

  const toLocalDateTimeInput = (isoValue) => {
    if (!isoValue) return '';
    return formatDateTimeInput(isoValue);
  };

  const toIsoString = (localValue) => {
    if (!localValue) return null;
    const date = zonedDateTimeInputToUtc(localValue);
    if (!date) return null;
    return date.toISOString();
  };

  useEffect(() => {
    if (!isOpen) return;
    setError(null);
    setIsSaving(false);
    if (mode === 'edit' && plant) {
      setLocalPlant({
        name: plant.name || '',
        plant_type: normalizePlantTypeId(plant.plant_type || DEFAULT_PLANT_TYPE_ID),
        strain: plant.strain || '',
        growth_stage: plant.growth_stage || '',
        planted_at: toLocalDateTimeInput(plant.planted_at),
        zone_id: plant.zone?.id ?? plant.zone_id ?? null,
      });
    } else {
      setLocalPlant({
        name: '',
        plant_type: DEFAULT_PLANT_TYPE_ID,
        strain: '',
        growth_stage: '',
        planted_at: '',
        zone_id: null,
      });
    }
  }, [isOpen, mode, plant]);

  if (!isOpen) {
    return null;
  }

  const handleZoneChange = (value) => {
    setLocalPlant((prev) => ({
      ...prev,
      zone_id: value === '' || value === null ? null : Number(value),
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

  const handleSave = async () => {
    if (!localPlant.name.trim()) {
      setError(translateApp("Укажите название растения"));
      return;
    }
    setIsSaving(true);
    setError(null);
    const payload = {
      name: localPlant.name.trim(),
      plant_type: localPlant.plant_type || null,
      strain: localPlant.strain || null,
      growth_stage: localPlant.growth_stage || null,
      zone_id: localPlant.zone_id ?? null,
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
      setError(err?.message || translateApp("Не удалось сохранить растение"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeletePlant = async () => {
    if (!plant?.id) return;
    const confirmed = window.confirm(translateApp("Точно удалить растение?"));
    if (!confirmed) return;
    setIsSaving(true);
    setError(null);
    try {
      await deletePlant(token, plant.id);
      onSaved?.();
      onClose?.();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || translateApp("Не удалось удалить растение"));
    } finally {
      setIsSaving(false);
    }
  };

  const title = mode === 'create' ? translateApp("Новое растение") : translateApp("Редактировать растение");

  const footer = (
    <div className="plant-dialog__footer">
      {mode === 'edit' && plant?.id && (
        <Button variant="danger" onClick={handleDeletePlant} disabled={isSaving}>{translateApp("Удалить")}</Button>
      )}
      <div className="plant-dialog__footer-actions">
        <Button variant="secondary" onClick={onClose} disabled={isSaving}>{translateApp("Отмена")}</Button>
        <Button variant="primary" onClick={handleSave} disabled={isSaving}>
          {isSaving ? translateApp("Сохранение...") : translateApp("Сохранить")}
        </Button>
      </div>
    </div>
  );

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title} disableOverlayClose footer={footer}>
      {error && <div className="plant-dialog__error">{error}</div>}

      <div className="plant-dialog__body">
        <FormField label={translateApp("Название")} htmlFor="plant-name" className="plant-dialog__field">
          <input
            id="plant-name"
            value={localPlant.name}
            onChange={(e) => setLocalPlant((prev) => ({ ...prev, name: e.target.value }))}
            placeholder="Basil"
          />
        </FormField>

        <FormField label={translateApp("Тип растения")} htmlFor="plant-type" className="plant-dialog__field">
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

        <FormField label={translateApp("Сорт")} htmlFor="plant-strain" className="plant-dialog__field">
          <input
            id="plant-strain"
            value={localPlant.strain || ''}
            onChange={(e) => setLocalPlant((prev) => ({ ...prev, strain: e.target.value }))}
          />
        </FormField>

        <FormField label={translateApp("Стадия роста")} htmlFor="growth-stage" className="plant-dialog__field">
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

        <FormField label={translateApp("Дата посадки")} htmlFor="planted-at" className="plant-dialog__field">
          <input
            id="planted-at"
            type="datetime-local"
            value={localPlant.planted_at || ''}
            onChange={(e) => setLocalPlant((prev) => ({ ...prev, planted_at: e.target.value }))}
          />
        </FormField>

        <FormField label={translateApp("Теплица")} htmlFor="plant-zone" className="plant-dialog__field">
          <select
            id="plant-zone"
            value={localPlant.zone_id ?? ''}
            onChange={(e) => handleZoneChange(e.target.value)}
          >
            <option value="">{translateApp("Без теплицы")}</option>
            {(Array.isArray(zones) ? zones : []).map((zone) => (
              <option key={zone.id} value={zone.id}>{zone.name}</option>
            ))}
          </select>
        </FormField>
      </div>
    </Modal>
  );
}

export default PlantEditDialog;
