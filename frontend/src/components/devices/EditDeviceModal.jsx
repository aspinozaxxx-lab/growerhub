import React, { useEffect, useMemo, useState } from 'react';
import { updateSensorBindings } from '../../api/sensors';
import { isSessionExpiredError } from '../../api/client';
import Modal from '../ui/Modal';
import Button from '../ui/Button';
import './EditDeviceModal.css';
import { translateApp } from '../../locales/i18n';

const SENSOR_TYPE_LABELS = {
  SOIL_MOISTURE: translateApp("Влажность почвы"),
  AIR_TEMPERATURE: translateApp("Температура воздуха"),
  AIR_HUMIDITY: translateApp("Влажность воздуха"),
};

const MS_IN_DAY = 24 * 60 * 60 * 1000;

function buildSensorTitle(sensor) {
  const label = sensor?.label;
  const type = sensor?.type;
  const channel = sensor?.channel;
  const base = label || SENSOR_TYPE_LABELS[type] || type || translateApp("Датчик");
  return channel !== undefined && channel !== null ? translateApp("{{value1}} · канал {{value2}}", { value1: base, value2: channel }) : base;
}

function formatPlantAge(plantedAt) {
  if (!plantedAt) {
    return translateApp("нет даты");
  }
  const date = new Date(plantedAt);
  if (Number.isNaN(date.getTime())) {
    return translateApp("нет даты");
  }
  const days = Math.max(0, Math.floor((Date.now() - date.getTime()) / MS_IN_DAY));
  if (days < 14) {
    return translateApp("{{value1}} дн.", { value1: days });
  }
  const weeks = Math.max(1, Math.floor(days / 7));
  return translateApp("{{value1}} нед.", { value1: weeks });
}

function buildPlantLabel(plant) {
  const idPart = plant?.id !== null && plant?.id !== undefined ? plant.id : '-';
  const namePart = plant?.name || translateApp("Без названия");
  const groupPart = plant?.plant_group?.name || translateApp("Без группы");
  const agePart = formatPlantAge(plant?.planted_at);
  return `${idPart} · ${namePart} · ${groupPart} · ${agePart}`;
}

function EditDeviceModal({
  device,
  plants,
  onClose,
  onSaved,
  token,
}) {
  const [sensorBindings, setSensorBindings] = useState({});
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState(null);

  const sensors = useMemo(() => (Array.isArray(device?.sensors) ? device.sensors : []), [device?.sensors]);

  const sortedPlants = useMemo(() => {
    const list = Array.isArray(plants) ? plants.slice() : [];
    return list.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
  }, [plants]);

  useEffect(() => {
    if (!device) {
      setSensorBindings({});
      return;
    }
    const nextSensors = {};
    sensors.forEach((sensor) => {
      const bound = Array.isArray(sensor.bound_plants) ? sensor.bound_plants.map((p) => p.id) : [];
      nextSensors[sensor.id] = bound;
    });
    setSensorBindings(nextSensors);
    setError(null);
  }, [device, sensors]);

  if (!device) {
    return null;
  }

  const toggleSensorPlant = (sensorId, plantId) => {
    setSensorBindings((prev) => {
      const current = prev[sensorId] || [];
      const exists = current.includes(plantId);
      const next = exists ? current.filter((id) => id !== plantId) : [...current, plantId];
      return {
        ...prev,
        [sensorId]: next,
      };
    });
  };

  const handleSave = async () => {
    setError(null);
    setIsSaving(true);
    try {
      await Promise.all(
        sensors.map((sensor) => updateSensorBindings(sensor.id, sensorBindings[sensor.id] || [], token)),
      );
      onSaved?.();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || translateApp("Не удалось сохранить привязки"));
    } finally {
      setIsSaving(false);
    }
  };

  const footer = (
    <div className="modal__actions">
      <Button variant="secondary" onClick={onClose} disabled={isSaving}>{translateApp("Отмена")}</Button>
      <Button
        type="button"
        variant="primary"
        onClick={handleSave}
        disabled={isSaving}
      >
        {isSaving ? translateApp("Сохраняем...") : translateApp("Сохранить")}
      </Button>
    </div>
  );

  return (
    <Modal
      isOpen={Boolean(device)}
      onClose={onClose}
      title={translateApp("Привязки датчиков")}
      closeLabel={translateApp("Закрыть")}
      disableOverlayClose
      footer={footer}
      size="lg"
    >
      <div className="edit-device__subtitle">{device.name || device.device_id}</div>
      {error && <div className="edit-device__error">{error}</div>}

      <div className="edit-device__section">
        <div className="edit-device__section-title">{translateApp("Датчики")}</div>
        {sensors.length === 0 && <div className="edit-device__empty">{translateApp("Нет датчиков")}</div>}
        {sensors.map((sensor) => (
          <div key={sensor.id} className="edit-device__item">
            <div className="edit-device__item-title">{buildSensorTitle(sensor)}</div>
            <div className="edit-device__bindings">
              {sortedPlants.length === 0 && <div className="edit-device__empty">{translateApp("Нет растений")}</div>}
              {sortedPlants.map((plant) => {
                const selected = (sensorBindings[sensor.id] || []).includes(plant.id);
                return (
                  <button
                    key={plant.id}
                    type="button"
                    className={`edit-device__pill ${selected ? 'is-selected' : ''}`}
                    onClick={() => toggleSensorPlant(sensor.id, plant.id)}
                  >
                    {buildPlantLabel(plant)}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </div>

    </Modal>
  );
}

export default EditDeviceModal;
