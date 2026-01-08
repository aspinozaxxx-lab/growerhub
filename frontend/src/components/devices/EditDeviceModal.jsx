import React, { useEffect, useMemo, useState } from 'react';
import { updateSensorBindings } from '../../api/sensors';
import { updatePumpBindings } from '../../api/pumps';
import { isSessionExpiredError } from '../../api/client';
import Modal from '../ui/Modal';
import Button from '../ui/Button';
import './EditDeviceModal.css';

const SENSOR_TYPE_LABELS = {
  SOIL_MOISTURE: 'Влажность почвы',
  AIR_TEMPERATURE: 'Температура воздуха',
  AIR_HUMIDITY: 'Влажность воздуха',
};

const DEFAULT_PUMP_RATE = 2000;
const MS_IN_DAY = 24 * 60 * 60 * 1000;

function buildSensorTitle(sensor) {
  const label = sensor?.label;
  const type = sensor?.type;
  const channel = sensor?.channel;
  const base = label || SENSOR_TYPE_LABELS[type] || type || 'Датчик';
  return channel !== undefined && channel !== null ? `${base} · канал ${channel}` : base;
}

function buildPumpTitle(pump) {
  const label = pump?.label;
  const channel = pump?.channel;
  const base = label || 'Насос';
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
  const [pumpBindings, setPumpBindings] = useState({});
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState(null);

  const sensors = Array.isArray(device?.sensors) ? device.sensors : [];
  const pumps = Array.isArray(device?.pumps) ? device.pumps : [];

  const sortedPlants = useMemo(() => {
    const list = Array.isArray(plants) ? plants.slice() : [];
    return list.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
  }, [plants]);

  useEffect(() => {
    if (!device) {
      setSensorBindings({});
      setPumpBindings({});
      return;
    }
    const nextSensors = {};
    sensors.forEach((sensor) => {
      const bound = Array.isArray(sensor.bound_plants) ? sensor.bound_plants.map((p) => p.id) : [];
      nextSensors[sensor.id] = bound;
    });
    const nextPumps = {};
    pumps.forEach((pump) => {
      const bound = {};
      if (Array.isArray(pump.bound_plants)) {
        pump.bound_plants.forEach((plant) => {
          bound[plant.id] = plant.rate_ml_per_hour ?? DEFAULT_PUMP_RATE;
        });
      }
      nextPumps[pump.id] = bound;
    });
    setSensorBindings(nextSensors);
    setPumpBindings(nextPumps);
    setError(null);
  }, [device, pumps, sensors]);

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

  const togglePumpPlant = (pumpId, plantId) => {
    setPumpBindings((prev) => {
      const current = prev[pumpId] || {};
      const next = { ...current };
      if (next[plantId] !== undefined) {
        delete next[plantId];
      } else {
        next[plantId] = DEFAULT_PUMP_RATE;
      }
      return {
        ...prev,
        [pumpId]: next,
      };
    });
  };

  const updatePumpRate = (pumpId, plantId, value) => {
    const parsed = Number(value);
    setPumpBindings((prev) => {
      const current = prev[pumpId] || {};
      return {
        ...prev,
        [pumpId]: {
          ...current,
          [plantId]: Number.isNaN(parsed) ? '' : parsed,
        },
      };
    });
  };

  const validatePumpRates = () => {
    for (const pump of pumps) {
      const bound = pumpBindings[pump.id] || {};
      for (const [plantId, rate] of Object.entries(bound)) {
        const parsed = Number(rate);
        if (!Number.isFinite(parsed) || parsed <= 0) {
          return `rate_ml_per_hour dolzhen byt' > 0 (plant ${plantId})`;
        }
      }
    }
    return null;
  };

  const handleSave = async () => {
    setError(null);
    const rateError = validatePumpRates();
    if (rateError) {
      setError(rateError);
      return;
    }

    setIsSaving(true);
    try {
      await Promise.all(
        sensors.map((sensor) => updateSensorBindings(sensor.id, sensorBindings[sensor.id] || [], token)),
      );
      await Promise.all(
        pumps.map((pump) => {
          const bound = pumpBindings[pump.id] || {};
          const items = Object.entries(bound).map(([plantId, rate]) => ({
            plant_id: Number(plantId),
            rate_ml_per_hour: Number(rate),
          }));
          return updatePumpBindings(pump.id, items, token);
        }),
      );
      onSaved?.();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Ne udalos sohranit privyazki');
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
      title="Привязки датчиков и насосов"
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

      <div className="edit-device__section">
        <div className="edit-device__section-title">Насосы</div>
        {pumps.length === 0 && <div className="edit-device__empty">Нет насосов</div>}
        {pumps.map((pump) => (
          <div key={pump.id} className="edit-device__item">
            <div className="edit-device__item-title">{buildPumpTitle(pump)}</div>
            <div className="edit-device__bindings edit-device__bindings--pump">
              {sortedPlants.length === 0 && <div className="edit-device__empty">Нет растений</div>}
              {sortedPlants.map((plant) => {
                const selected = pumpBindings[pump.id] && pumpBindings[pump.id][plant.id] !== undefined;
                return (
                  <div key={plant.id} className="edit-device__pump-row">
                    <button
                      type="button"
                      className={`edit-device__pill ${selected ? 'is-selected' : ''}`}
                      onClick={() => togglePumpPlant(pump.id, plant.id)}
                    >
                      {buildPlantLabel(plant)}
                    </button>
                    {selected && (
                      <input
                        type="number"
                        min="1"
                        step="50"
                        className="edit-device__rate"
                        value={pumpBindings[pump.id][plant.id]}
                        onChange={(e) => updatePumpRate(pump.id, plant.id, e.target.value)}
                        aria-label="rate_ml_per_hour"
                      />
                    )}
                  </div>
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
