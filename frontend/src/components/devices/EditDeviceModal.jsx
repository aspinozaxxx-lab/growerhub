import React, { useEffect, useMemo, useState } from 'react';
import { updateSensorBindings } from '../../api/sensors';
import { isSessionExpiredError } from '../../api/client';
import Modal from '../ui/Modal';
import Button from '../ui/Button';
import './EditDeviceModal.css';

const SENSOR_TYPE_LABELS = {
  SOIL_MOISTURE: 'Влажность почвы',
  AIR_TEMPERATURE: 'Температура воздуха',
  AIR_HUMIDITY: 'Влажность воздуха',
};

const MS_IN_DAY = 24 * 60 * 60 * 1000;

function buildSensorTitle(sensor) {
  const label = sensor?.label;
  const type = sensor?.type;
  const channel = sensor?.channel;
  const base = label || SENSOR_TYPE_LABELS[type] || type || 'Датчик';
  return channel !== undefined && channel !== null ? `${base} · канал ${channel}` : base;
}

function formatPlantAge(plantedAt) {
  if (!plantedAt) {
    return 'нет даты';
  }
  const date = new Date(plantedAt);
  if (Number.isNaN(date.getTime())) {
    return 'нет даты';
  }
  const days = Math.max(0, Math.floor((Date.now() - date.getTime()) / MS_IN_DAY));
  if (days < 14) {
    return `${days} дн.`;
  }
  const weeks = Math.max(1, Math.floor(days / 7));
  return `${weeks} нед.`;
}

function buildPlantLabel(plant) {
  const idPart = plant?.id !== null && plant?.id !== undefined ? plant.id : '-';
  const namePart = plant?.name || 'Без названия';
  const groupPart = plant?.plant_group?.name || 'Без группы';
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
      setError(err?.message || 'Не удалось сохранить привязки');
    } finally {
      setIsSaving(false);
    }
  };

  const footer = (
    <div className="modal__actions">
      <Button variant="secondary" onClick={onClose} disabled={isSaving}>
        Отмена
      </Button>
      <Button
        type="button"
        variant="primary"
        onClick={handleSave}
        disabled={isSaving}
      >
        {isSaving ? 'Сохраняем...' : 'Сохранить'}
      </Button>
    </div>
  );

  return (
    <Modal
      isOpen={Boolean(device)}
      onClose={onClose}
      title="Привязки датчиков"
      closeLabel="Закрыть"
      disableOverlayClose
      footer={footer}
      size="lg"
    >
      <div className="edit-device__subtitle">{device.name || device.device_id}</div>
      {error && <div className="edit-device__error">{error}</div>}

      <div className="edit-device__section">
        <div className="edit-device__section-title">Датчики</div>
        {sensors.length === 0 && <div className="edit-device__empty">Нет датчиков</div>}
        {sensors.map((sensor) => (
          <div key={sensor.id} className="edit-device__item">
            <div className="edit-device__item-title">{buildSensorTitle(sensor)}</div>
            <div className="edit-device__bindings">
              {sortedPlants.length === 0 && <div className="edit-device__empty">Нет растений</div>}
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
